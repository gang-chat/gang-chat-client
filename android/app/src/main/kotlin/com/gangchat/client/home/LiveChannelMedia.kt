package com.gangchat.client.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.liveParticipantDisplayName
import com.gangchat.client.app.liveScreenShareStageLabel
import com.gangchat.client.app.normalizedAudioVolume
import com.gangchat.client.app.userAvatarLabel
import com.gangchat.client.foundation.VisibleForTesting
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.live.LiveVideoTrack
import com.gangchat.client.live.LiveVideoTrackFit
import com.gangchat.client.live.LiveVideoTrackView
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.shell.FullScreenSystemUiController
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.MediaQuery
import com.gangchat.client.widgets.Orientation
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.ignorePointer
import com.gangchat.client.widgets.unplaced
import com.gangchat.client.widgets.rememberBuildContext
import com.gangchat.client.widgets.startTimer
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

private enum class _LiveMediaKind { camera, screenShare }

@Composable
internal fun _LiveMediaVideo(track: LiveVideoTrack, fit: LiveVideoTrackFit? = null, mirrored: Boolean = false) {
    LiveVideoTrackView(
        track = track,
        modifier = Modifier.fillMaxSize().testTag(liveVideoTrackViewTag),
        fit = fit ?: if (track.isScreenShare) LiveVideoTrackFit.contain else LiveVideoTrackFit.cover,
        mirrored = mirrored,
    )
}

@Composable
internal fun _LiveMediaStage(
    track: LiveVideoTrack,
    renderVideo: Boolean,
    label: String,
    screenShareViewers: List<UserSummary>,
    screenShareVolume: Double,
    cameraMirrored: Boolean,
    onFlipCamera: (suspend () -> Boolean)?,
    onSetLocalCameraMirrored: suspend (Boolean) -> Boolean,
    onExit: () -> Unit,
    onFullScreen: () -> Unit,
    onScreenShareVolumeChanged: (Double) -> Unit,
    onScreenShareMuteToggled: () -> Unit,
) = StatefulWidget(
    _LiveMediaStageWidget(
        track, renderVideo, label, screenShareViewers, screenShareVolume, cameraMirrored, onFlipCamera, onSetLocalCameraMirrored, onExit,
        onFullScreen, onScreenShareVolumeChanged, onScreenShareMuteToggled,
    ),
) { _LiveMediaStageState() }

private class _LiveMediaStageWidget(
    val track: LiveVideoTrack,
    val renderVideo: Boolean,
    val label: String,
    val screenShareViewers: List<UserSummary>,
    val screenShareVolume: Double,
    val cameraMirrored: Boolean,
    val onFlipCamera: (suspend () -> Boolean)?,
    val onSetLocalCameraMirrored: suspend (Boolean) -> Boolean,
    val onExit: () -> Unit,
    val onFullScreen: () -> Unit,
    val onScreenShareVolumeChanged: (Double) -> Unit,
    val onScreenShareMuteToggled: () -> Unit,
)

private class _LiveMediaStageState : ViewState<_LiveMediaStageWidget>() {
    private var _viewerMirrorOverride: Boolean? = null
    private var _pendingSharedMirror: Boolean? = null
    private var _flippingCamera = false
    private var _settingSharedMirror = false

    private val _cameraMirrored: Boolean
        get() {
            if (widget.track.isLocal) return _pendingSharedMirror ?: widget.cameraMirrored
            return _viewerMirrorOverride ?: widget.cameraMirrored
        }

    override fun didUpdateWidget(oldWidget: _LiveMediaStageWidget) {
        val trackChanged =
            oldWidget.track.identity != widget.track.identity ||
                oldWidget.track.isScreenShare != widget.track.isScreenShare ||
                oldWidget.track.isLocal != widget.track.isLocal
        if (trackChanged) {
            _viewerMirrorOverride = null
            _pendingSharedMirror = null
            _flippingCamera = false
            _settingSharedMirror = false
            return
        }
        if (_pendingSharedMirror == widget.cameraMirrored) _pendingSharedMirror = null
    }

    private suspend fun _flipCamera() {
        val callback = widget.onFlipCamera
        if (callback == null || _flippingCamera) return
        setState { _flippingCamera = true }
        callback()
        if (mounted) setState { _flippingCamera = false }
    }

    private suspend fun _toggleMirror() {
        val mirrored = !_cameraMirrored
        if (!widget.track.isLocal) {
            setState { _viewerMirrorOverride = mirrored }
            return
        }
        if (_settingSharedMirror) return
        setState {
            _settingSharedMirror = true
            _pendingSharedMirror = mirrored
        }
        val updated = widget.onSetLocalCameraMirrored(mirrored)
        if (!mounted) return
        setState {
            _settingSharedMirror = false
            if (!updated) _pendingSharedMirror = null
        }
    }

    @Composable
    override fun build() {
        val track = widget.track
        val isRemoteScreenShare = track.isScreenShare && !track.isLocal
        val stageModifier =
            if (track.isScreenShare) {
                Modifier.fillMaxSize().clipToBounds()
            } else {
                Modifier.fillMaxSize().clip(RoundedCornerShape(UiRadii.lg))
            }
        Box(stageModifier.background(UiColors.surfacePressed)) {
            if (widget.renderVideo) {
                _LiveMediaVideo(track = track, fit = LiveVideoTrackFit.contain, mirrored = _cameraMirrored)
            } else {
                Box(Modifier.fillMaxSize().testTag("live-stage:video-suspended"))
            }
            Box(Modifier.align(Alignment.TopStart)) {
                _LiveStageBadge(label = widget.label, kind = if (track.isScreenShare) _LiveMediaKind.screenShare else _LiveMediaKind.camera)
            }
            if (track.isScreenShare && widget.screenShareViewers.isNotEmpty()) {
                Box(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp)) {
                    _ScreenShareViewerPreview(viewers = widget.screenShareViewers)
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)) {
                _StageOverlayIconButton(
                    modifier = Modifier.testTag("live-stage:exit"),
                    icon = Icons.Filled.CloseFullscreen,
                    infoMessage = "退出焦点画面",
                    onPressed = widget.onExit,
                )
                Spacer(Modifier.width(6.dp))
                _StageOverlayIconButton(
                    modifier = Modifier.testTag("live-stage:fullscreen"),
                    icon = Icons.Filled.Fullscreen,
                    infoMessage = "全屏查看",
                    onPressed = widget.onFullScreen,
                )
            }
            if (isRemoteScreenShare) {
                Box(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp)) {
                    _StageScreenShareVolumeButton(
                        value = widget.screenShareVolume,
                        onChanged = widget.onScreenShareVolumeChanged,
                        onPressed = widget.onScreenShareMuteToggled,
                    )
                }
            }
            if (!track.isScreenShare) {
                Row(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp)) {
                    if (track.isLocal && widget.onFlipCamera != null) {
                        _StageOverlayIconButton(
                            modifier = Modifier.testTag("live-stage:flip-camera"),
                            icon = Icons.Filled.FlipCameraAndroid,
                            infoMessage = "翻转摄像头",
                            onPressed = if (_flippingCamera) null else ({ unawaited { _flipCamera() } }),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    _StageOverlayIconButton(
                        modifier = Modifier.testTag("live-stage:mirror-camera"),
                        icon = Icons.Filled.Flip,
                        infoMessage = if (_cameraMirrored) "取消镜像" else "镜像",
                        active = _cameraMirrored,
                        onPressed = if (_settingSharedMirror) null else ({ unawaited { _toggleMirror() } }),
                    )
                }
            }
        }
    }
}

@Composable
private fun _StageScreenShareVolumeButton(value: Double, onChanged: (Double) -> Unit, onPressed: () -> Unit) {
    val normalized = normalizedAudioVolume(value)
    val message = if (normalized <= 0) "取消静音共享屏幕" else "静音共享屏幕"
    _HoverVolumeButton(
        modifier = Modifier.testTag("live-stage:screen-share-volume"),
        value = normalized,
        semanticLabel = "共享屏幕输出音量",
        infoMessage = message,
        onChanged = onChanged,
        panelWidth = 34.0,
        panelHeight = _hoverVolumePanelHeight * 34 / _controlButtonSize,
        childUsesTapGesture = true,
    ) {
        Box(
            Modifier
                ._liveTap(onPressed)
                .semantics {
                    role = Role.Button
                    contentDescription = message
                },
        ) {
            _StageOverlayIconSurface(icon = _screenShareVolumeIcon(normalized))
        }
    }
}

/**
 * The full-screen camera / screen-share viewer. Its overlay controls hide
 * after 3 s without interaction (the system bars follow them through
 * [systemUiController]) and reappear on any touch; Escape exits.
 */
@Composable
fun LiveFullScreenStage(
    track: LiveVideoTrack,
    label: String,
    screenShareVolume: Double,
    onScreenShareVolumeChanged: (Double) -> Unit,
    onScreenShareMuteToggled: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    cameraMirrored: Boolean = false,
    screenShareViewers: List<UserSummary> = emptyList(),
    systemUiController: FullScreenSystemUiController? = null,
) = StatefulWidget(
    LiveFullScreenStageWidget(
        track, label, cameraMirrored, screenShareViewers, screenShareVolume, onScreenShareVolumeChanged, onScreenShareMuteToggled, onExit,
        systemUiController, modifier,
    ),
) { _LiveFullScreenStageState() }

class LiveFullScreenStageWidget(
    val track: LiveVideoTrack,
    val label: String,
    val cameraMirrored: Boolean,
    val screenShareViewers: List<UserSummary>,
    val screenShareVolume: Double,
    val onScreenShareVolumeChanged: (Double) -> Unit,
    val onScreenShareMuteToggled: () -> Unit,
    val onExit: () -> Unit,
    val systemUiController: FullScreenSystemUiController?,
    val modifier: Modifier,
)

/** Test tag of the full-screen stage root (Dart's `find.byType(LiveFullScreenStage)`). */
const val liveFullScreenStageTag = "live-fullscreen-stage"

private class _LiveFullScreenStageState : ViewState<LiveFullScreenStageWidget>() {
    private val _focusNode = FocusRequester()
    private var _controlsHideTimer: CompositionTimer? = null
    private var _viewportOrientation: Orientation? = null
    private lateinit var _systemUiController: FullScreenSystemUiController
    private var _controlsTimerGeneration = 0
    private var _controlsVisible = true
    private var _scope: CoroutineScope? = null

    override fun initState() {
        _systemUiController = widget.systemUiController ?: FullScreenSystemUiController()
        val controller = _systemUiController
        unawaited { _ignoreSystemUiFailure { controller.setControlsVisible(true) } }
    }

    override fun didUpdateWidget(oldWidget: LiveFullScreenStageWidget) {
        if (oldWidget.systemUiController !== widget.systemUiController) {
            val previous = _systemUiController
            unawaited { _ignoreSystemUiFailure { previous.restore() } }
            _systemUiController = widget.systemUiController ?: FullScreenSystemUiController()
            val next = _systemUiController
            val visible = _controlsVisible
            unawaited { _ignoreSystemUiFailure { next.setControlsVisible(visible) } }
        }
        if (oldWidget.track.identity != widget.track.identity || oldWidget.track.isScreenShare != widget.track.isScreenShare) {
            _showControlsAndRestartTimer()
        }
    }

    // Dart's didChangeDependencies: on Android a rotation is itself a
    // user-visible transition. The build already runs, so the field is set
    // directly instead of through setState.
    private fun _syncViewport() {
        val nextOrientation = MediaQuery.orientationOf(context)
        val viewportChanged = _viewportOrientation != null && _viewportOrientation != nextOrientation
        _viewportOrientation = nextOrientation
        if (viewportChanged) {
            _controlsVisible = true
            _scheduleControlsAutoHide()
        }
    }

    private fun _scheduleControlsAutoHide() {
        val generation = ++_controlsTimerGeneration
        _controlsHideTimer?.cancel()
        _controlsHideTimer =
            _scope?.startTimer(_controlsAutoHideDelay) {
                if (!mounted || generation != _controlsTimerGeneration || !_controlsVisible) return@startTimer
                setState { _controlsVisible = false }
                val controller = _systemUiController
                unawaited { _ignoreSystemUiFailure { controller.setControlsVisible(false) } }
            }
    }

    fun _showControlsAndRestartTimer() {
        if (!mounted) return
        if (!_controlsVisible) {
            setState { _controlsVisible = true }
            val controller = _systemUiController
            unawaited { _ignoreSystemUiFailure { controller.setControlsVisible(true) } }
        }
        _scheduleControlsAutoHide()
    }

    private suspend fun _ignoreSystemUiFailure(operation: suspend () -> Unit) {
        try {
            operation()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // System UI is best-effort and must never block media interaction.
        }
    }

    override fun dispose() {
        _controlsTimerGeneration++
        _controlsHideTimer?.cancel()
        val controller = _systemUiController
        unawaited { _ignoreSystemUiFailure { controller.restore() } }
    }

    @Composable
    override fun build() {
        val firstBuild = _scope == null
        _scope = rememberCoroutineScope()
        if (firstBuild) _scheduleControlsAutoHide()
        _syncViewport()
        LaunchedEffect(Unit) { runCatching { _focusNode.requestFocus() } }
        val safeInsets = MediaQuery.viewPaddingOf(context)
        val track = widget.track
        Box(
            widget.modifier
                .fillMaxSize()
                .testTag(liveFullScreenStageTag)
                .focusRequester(_focusNode)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        widget.onExit()
                        true
                    } else {
                        false
                    }
                }
                .focusable()
                // Dart's MouseRegion(onEnter, onHover) + Listener: every
                // pointer event shows the controls and restarts the timeout.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        // Compose re-sends the last hover position after a
                        // relayout (hover changes report no previous position
                        // of their own); Flutter's onPointerHover only fires for
                        // real movement, so skip moves that land where the
                        // previous event was.
                        var lastPosition: Offset? = null
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val position = event.changes.firstOrNull()?.position
                            val moved = position != lastPosition
                            lastPosition = position
                            when (event.type) {
                                PointerEventType.Press, PointerEventType.Release,
                                PointerEventType.Enter, PointerEventType.Scroll,
                                -> _showControlsAndRestartTimer()
                                PointerEventType.Move ->
                                    if (moved || event.changes.any { it.pressed }) _showControlsAndRestartTimer()
                                else -> Unit
                            }
                        }
                    }
                }
                .background(Color.Black),
        ) {
            _LiveMediaVideo(track = track, fit = LiveVideoTrackFit.contain, mirrored = widget.cameraMirrored)
            _FullScreenHoverReveal(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp + safeInsets.left, top = 14.dp + safeInsets.top),
                tag = "live-fullscreen-stage:label-reveal",
                visible = _controlsVisible,
            ) {
                _LiveStageBadge(label = widget.label, kind = if (track.isScreenShare) _LiveMediaKind.screenShare else _LiveMediaKind.camera)
            }
            if (track.isScreenShare && widget.screenShareViewers.isNotEmpty()) {
                _FullScreenHoverReveal(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp + safeInsets.left, bottom = 14.dp + safeInsets.bottom),
                    tag = "live-fullscreen-stage:screen-viewers-reveal",
                    visible = _controlsVisible,
                ) {
                    _ScreenShareViewerPreview(viewers = widget.screenShareViewers)
                }
            }
            _FullScreenHoverReveal(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp + safeInsets.top, end = 14.dp + safeInsets.right),
                tag = "live-fullscreen-stage:exit-reveal",
                visible = _controlsVisible,
            ) {
                _StageOverlayIconButton(
                    modifier = Modifier.testTag("live-fullscreen-stage:exit"),
                    icon = Icons.Filled.FullscreenExit,
                    infoMessage = "退出全屏",
                    onPressed = widget.onExit,
                )
            }
            if (track.isScreenShare && !track.isLocal) {
                _FullScreenHoverReveal(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp + safeInsets.right, bottom = 14.dp + safeInsets.bottom),
                    tag = "live-fullscreen-stage:volume-reveal",
                    visible = _controlsVisible,
                ) {
                    _StageScreenShareVolumeButton(
                        value = widget.screenShareVolume,
                        onChanged = widget.onScreenShareVolumeChanged,
                        onPressed = widget.onScreenShareMuteToggled,
                    )
                }
            }
        }
    }

    companion object {
        private val _controlsAutoHideDelay = 3.seconds
    }
}

@Composable
private fun _StageOverlayIconButton(
    icon: ImageVector,
    infoMessage: String,
    onPressed: (() -> Unit)?,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) = StatefulWidget(_StageOverlayIconButtonWidget(icon, infoMessage, onPressed, active, modifier)) { _StageOverlayIconButtonState() }

private class _StageOverlayIconButtonWidget(
    val icon: ImageVector,
    val infoMessage: String,
    val onPressed: (() -> Unit)?,
    val active: Boolean,
    val modifier: Modifier,
)

private class _StageOverlayIconButtonState : ViewState<_StageOverlayIconButtonWidget>() {
    private var _hovered = false

    @Composable
    override fun build() {
        val enabled = widget.onPressed != null
        _HoverInfo(message = widget.infoMessage, modifier = widget.modifier) {
            Box(
                Modifier
                    ._liveMouseHover(onEnter = { setState { _hovered = true } }, onExit = { setState { _hovered = false } })
                    ._liveTap(widget.onPressed)
                    .semantics {
                        role = Role.Button
                        if (!enabled) disabled()
                        selected = widget.active
                    },
            ) {
                _StageOverlayIconSurface(icon = widget.icon, hovered = _hovered, active = widget.active)
            }
        }
    }
}

@Composable
private fun _StageOverlayIconSurface(icon: ImageVector, hovered: Boolean? = null, active: Boolean = false) =
    StatefulWidget(_StageOverlayIconSurfaceWidget(icon, hovered, active)) { _StageOverlayIconSurfaceState() }

private class _StageOverlayIconSurfaceWidget(val icon: ImageVector, val hovered: Boolean?, val active: Boolean)

private class _StageOverlayIconSurfaceState : ViewState<_StageOverlayIconSurfaceWidget>() {
    private var _hovered = false

    private val _effectiveHovered: Boolean get() = widget.hovered ?: _hovered

    @Composable
    override fun build() {
        val highlighted = _effectiveHovered || widget.active
        val color = if (highlighted) UiColors.accent else UiColors.textSecondary
        val background by animateColorAsState(
            if (highlighted) UiColors.surface.copy(alpha = 0.92f) else UiColors.surface.copy(alpha = 0.72f),
            tween(120),
            label = "stageOverlayIconSurface",
        )
        val surfaceModifier =
            if (widget.hovered != null) {
                Modifier
            } else {
                Modifier._liveMouseHover(onEnter = { setState { _hovered = true } }, onExit = { setState { _hovered = false } })
            }
        Box(
            surfaceModifier.size(34.dp).decoration(background, RoundedCornerShape(UiRadii.md)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(widget.icon, size = 19.dp, color = color)
        }
    }
}

@Composable
private fun _LiveStageBadge(label: String, kind: _LiveMediaKind) {
    Row(
        Modifier
            .drawBehind {
                drawRect(UiColors.surface.copy(alpha = 0.86f))
                val stroke = 1.dp.toPx()
                drawRect(UiColors.border, topLeft = Offset(size.width - stroke, 0f), size = androidx.compose.ui.geometry.Size(stroke, size.height))
                drawRect(UiColors.border, topLeft = Offset(0f, size.height - stroke), size = androidx.compose.ui.geometry.Size(size.width, stroke))
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(_mediaIcon(kind), color = UiColors.accent, size = 15.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = UiTypography.label.copyWith(color = UiColors.text),
        )
    }
}

@Composable
private fun _ScreenShareViewerPreview(viewers: List<UserSummary>) {
    val avatarSize = 24.0
    val overlap = 8.0
    val context = rememberBuildContext()
    val config = AppConfigScope.of(context)
    val visibleViewers = viewers.take(5)
    val avatarWidth = avatarSize + (visibleViewers.size - 1) * (avatarSize - overlap)
    Row(
        Modifier
            .testTag("live-stage:screen-viewers")
            .decoration(UiColors.surface.copy(alpha = 0.9f), RoundedCornerShape(UiRadii.md), UiColors.border)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Visibility,
            modifier = Modifier.testTag("live-stage:screen-viewers-icon"),
            color = UiColors.accent,
            size = 17.dp,
        )
        Spacer(Modifier.width(7.dp))
        Box(Modifier.size(avatarWidth.dp, avatarSize.dp)) {
            for ((index, viewer) in visibleViewers.withIndex()) {
                Avatar(
                    modifier = Modifier.absoluteOffset(x = (index * (avatarSize - overlap)).dp),
                    label = userAvatarLabel(viewer),
                    imageUrl = config.resolveAssetUrl(viewer.avatarUrl),
                    defaultAvatarKey = viewer.defaultAvatarKey,
                    size = avatarSize.dp,
                    active = true,
                    activeBorderWidth = 1.dp,
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            "共 ${viewers.size} 人",
            style = UiTypography.label.copyWith(color = Color.White, fontWeight = FontWeight.W600, fontSize = 11.0),
        )
    }
}

@Composable
private fun _FullScreenHoverReveal(visible: Boolean, tag: String, modifier: Modifier = Modifier, child: @Composable () -> Unit) {
    val opacity by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(if (visible) 0 else 160),
        label = "fullScreenHoverReveal",
    )
    Box(
        modifier
            .testTag(tag)
            .semantics { liveOpacity = if (visible) 1f else 0f }
            // Flutter's IgnorePointer(ignoring: !visible): once faded out the
            // controls are left unplaced so taps reach the stage underneath
            // (which brings them back); during the fade they only absorb.
            .then(
                when {
                    visible -> Modifier
                    opacity <= 0f -> Modifier.unplaced()
                    else -> Modifier.ignorePointer()
                },
            )
            .alpha(opacity),
    ) {
        child()
    }
}

private fun _mediaIcon(kind: _LiveMediaKind): ImageVector =
    when (kind) {
        _LiveMediaKind.camera -> Icons.Filled.Videocam
        _LiveMediaKind.screenShare -> Icons.AutoMirrored.Filled.ScreenShare
    }

private fun _screenShareVolumeIcon(value: Double): ImageVector = _volumeLevelIcon(normalizedAudioVolume(value))

fun liveStageTrackLabel(live: LiveState?, track: LiveVideoTrack): String {
    val name = liveParticipantDisplayName(live, track.identity)
    if (track.isScreenShare) return liveScreenShareStageLabel(name)
    return if (name.isEmpty()) "摄像头" else "$name 的摄像头"
}

private fun _trackForSelection(tracks: List<LiveVideoTrack>, selection: LiveStageSelection): LiveVideoTrack? {
    if (selection.mode != LiveStageSelectionMode.track) return null
    for (track in tracks) {
        if (track.identity == selection.identity && track.isScreenShare == selection.isScreenShare) return track
    }
    return null
}

@VisibleForTesting
fun resolveLiveStageTrackForTest(tracks: List<LiveVideoTrack>, selection: LiveStageSelection?): LiveVideoTrack? =
    _resolveLiveStageTrack(tracks = tracks, selection = selection)

internal fun _resolveLiveStageTrack(tracks: List<LiveVideoTrack>, selection: LiveStageSelection?): LiveVideoTrack? {
    if (selection != null) {
        if (selection.mode == LiveStageSelectionMode.none) return null
        val track = _trackForSelection(tracks, selection)
        if (track != null) return track
        return null
    }
    return _localScreenShare(tracks)
}

private fun _localScreenShare(tracks: List<LiveVideoTrack>): LiveVideoTrack? {
    for (track in tracks) {
        if (track.isLocal && track.isScreenShare) return track
    }
    return null
}

internal fun _memberPreviewTrack(tracks: List<LiveVideoTrack>, userId: String, stageTrack: LiveVideoTrack?): LiveVideoTrack? {
    val camera = _cameraFor(tracks, userId)
    val share = _screenShareFor(tracks, userId)
    if (stageTrack?.identity == userId) {
        if (stageTrack.isScreenShare) return camera
        return share
    }
    return camera ?: share
}

internal fun _selectableTrack(tracks: List<LiveVideoTrack>, userId: String, stageTrack: LiveVideoTrack?): LiveVideoTrack? {
    val preview = _memberPreviewTrack(tracks = tracks, userId = userId, stageTrack = stageTrack)
    if (preview != null) return preview
    return null
}

private fun _screenShareFor(tracks: List<LiveVideoTrack>, userId: String): LiveVideoTrack? {
    for (track in tracks) {
        if (track.isScreenShare && track.identity == userId) return track
    }
    return null
}

private fun _cameraFor(tracks: List<LiveVideoTrack>, userId: String): LiveVideoTrack? {
    for (track in tracks) {
        if (!track.isScreenShare && track.identity == userId) return track
    }
    return null
}
