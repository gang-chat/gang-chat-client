package com.gangchat.client.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MusicBoxController
import com.gangchat.client.app.MusicBoxTransportAction
import com.gangchat.client.app.audioVolumePercentText
import com.gangchat.client.app.liveMicControlState
import com.gangchat.client.app.musicBoxPrimaryTransport
import com.gangchat.client.app.musicBoxProgress
import com.gangchat.client.app.musicBoxRecordSpinning
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.OverflowMarqueeText
import com.gangchat.client.ui.TooltipTriggerMode
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTooltip
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.widgets.BoxShadow
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.OverlayEntry
import com.gangchat.client.widgets.OverlayState
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.kLongPressTimeout
import com.gangchat.client.widgets.kTouchSlop
import com.gangchat.client.widgets.startTimer
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun _LiveControlBar(
    joined: Boolean,
    joining: Boolean,
    micMuted: Boolean,
    headphonesMuted: Boolean,
    voiceBlocked: Boolean,
    cameraOn: Boolean,
    screenSharing: Boolean,
    inputVolume: Double,
    outputVolume: Double,
    musicBox: MusicBoxState?,
    musicBoxEnabled: Boolean,
    musicBoxOpen: Boolean,
    onJoin: (() -> Unit)?,
    onLeave: () -> Unit,
    onToggleMic: (() -> Unit)?,
    onToggleHeadphones: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleShare: (() -> Unit)?,
    onInputVolumeChanged: (Double) -> Unit,
    onOutputVolumeChanged: (Double) -> Unit,
    onToggleMusicBox: () -> Unit,
    onMusicBoxTogglePlayback: () -> Unit,
    onMusicBoxSkip: () -> Unit,
    musicBoxController: MusicBoxController?,
    musicBoxRoomId: String?,
    onMusicBoxStateChanged: ((MusicBoxState) -> Unit)?,
    currentUser: CurrentUser,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    userProfileActionBuilder: UserProfileActionBuilder?,
    onCollapse: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 430.dp
        val micControl = liveMicControlState(micMuted = micMuted, voiceBlocked = voiceBlocked)
        val controls = mutableListOf<@Composable () -> Unit>()
        controls += {
            _HoverVolumeButton(
                modifier = Modifier.testTag("live-control:mic"),
                value = inputVolume,
                semanticLabel = "麦克风输入音量",
                infoMessage = if (micControl.mutedForDisplay) "打开麦克风" else "关闭麦克风",
                onChanged = onInputVolumeChanged,
            ) {
                ButtonIcon(
                    icon = { Icon(if (micControl.mutedForDisplay) Icons.Filled.MicOff else Icons.Filled.Mic) },
                    selected = micControl.active,
                    onPressed = if (micControl.enabled) onToggleMic else null,
                    size = _controlButtonSize.dp,
                )
            }
        }
        controls += {
            _HoverVolumeButton(
                modifier = Modifier.testTag("live-control:headphones"),
                value = outputVolume,
                semanticLabel = "语音输出音量",
                infoMessage = if (headphonesMuted) "打开耳机" else "关闭耳机",
                onChanged = onOutputVolumeChanged,
            ) {
                ButtonIcon(
                    icon = { Icon(if (headphonesMuted) Icons.Filled.HeadsetOff else Icons.Filled.Headphones) },
                    selected = !headphonesMuted,
                    onPressed = onToggleHeadphones,
                    size = _controlButtonSize.dp,
                )
            }
        }
        controls += {
            _HoverInfo(message = if (cameraOn) "关闭摄像头" else "开启摄像头") {
                ButtonIcon(
                    modifier = Modifier.testTag("live-control:camera"),
                    icon = { Icon(if (cameraOn) Icons.Filled.Videocam else Icons.Outlined.Videocam) },
                    selected = cameraOn,
                    onPressed = onToggleCamera,
                    size = _controlButtonSize.dp,
                )
            }
        }
        if (onToggleShare != null) {
            controls += {
                _HoverInfo(message = if (screenSharing) "停止共享屏幕" else "共享屏幕") {
                    ButtonIcon(
                        modifier = Modifier.testTag("live-control:screen-share"),
                        icon = { Icon(if (screenSharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Outlined.ScreenShare) },
                        selected = screenSharing,
                        onPressed = onToggleShare,
                        size = _controlButtonSize.dp,
                    )
                }
            }
        }
        controls += {
            _HoverInfo(message = if (joining) "正在加入语音" else "离开语音频道") {
                ButtonIcon(
                    modifier = Modifier.testTag("live-control:leave"),
                    icon = { Icon(Icons.Filled.CallEnd) },
                    tone = ButtonTone.danger,
                    onPressed = if (joining) null else onLeave,
                    size = _controlButtonSize.dp,
                )
            }
        }

        val musicBoxStrip: (@Composable () -> Unit)? =
            if (joined && musicBoxEnabled && musicBox != null) {
                {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        _InlineMusicBox(
                            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
                            state = musicBox,
                            expanded = musicBoxOpen,
                            onTogglePlayback = onMusicBoxTogglePlayback,
                            onSkip = onMusicBoxSkip,
                            onToggleExpand = onToggleMusicBox,
                            controller = musicBoxController,
                            roomId = musicBoxRoomId,
                            onStateChanged = onMusicBoxStateChanged,
                            currentUser = currentUser,
                            onResolveUserProfile = onResolveUserProfile,
                            onResolveRoomProfile = onResolveRoomProfile,
                            onEnterCommonRoom = onEnterCommonRoom,
                            userProfileActionBuilder = userProfileActionBuilder,
                        )
                    }
                }
            } else {
                null
            }

        @Composable
        fun compactCollapseButton(showLabel: Boolean, modifier: Modifier = Modifier) {
            _HoverInfo(message = "收起语音频道", modifier = modifier) {
                if (showLabel) {
                    Button(
                        modifier = Modifier.testTag("live-control:collapse"),
                        width = Dp.Infinity,
                        height = _controlButtonSize.dp,
                        icon = { Icon(Icons.Filled.KeyboardArrowUp) },
                        onPressed = onCollapse,
                    ) {
                        Text("收起")
                    }
                } else {
                    ButtonIcon(
                        modifier = Modifier.testTag("live-control:collapse"),
                        icon = { Icon(Icons.Filled.KeyboardArrowUp) },
                        onPressed = onCollapse,
                        size = _controlButtonSize.dp,
                    )
                }
            }
        }

        if (compact) {
            val maxWidthValue = maxWidth.value.toDouble()
            val joinedControlsFitOneRow =
                (controls.size + 1) * _controlButtonSize + controls.size * 10 <= maxWidthValue
            Column(Modifier.fillMaxWidth()) {
                if (musicBoxStrip != null) {
                    musicBoxStrip()
                    Spacer(Modifier.height(10.dp))
                }
                when {
                    !joined -> {
                        _HoverInfo(message = if (joining) "正在加入语音" else "加入语音频道", modifier = Modifier.fillMaxWidth()) {
                            Button(
                                modifier = Modifier.testTag("live-control:join"),
                                width = Dp.Infinity,
                                height = _controlButtonSize.dp,
                                loading = joining,
                                icon = { Icon(Icons.Filled.Call) },
                                onPressed = onJoin,
                            ) {
                                Text("加入")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        compactCollapseButton(showLabel = true, modifier = Modifier.fillMaxWidth())
                    }
                    joinedControlsFitOneRow ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                _withControlGaps(controls + listOf<@Composable () -> Unit>({ compactCollapseButton(showLabel = false) }))
                            }
                        }
                    else -> {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            for (control in controls) control()
                        }
                        Spacer(Modifier.height(10.dp))
                        compactCollapseButton(showLabel = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            return@BoxWithConstraints
        }

        // The inline music box sits on its own centered row above the
        // transport buttons, so it never competes with them for horizontal
        // space — it just ellipsizes its title/artist within the 280 cap.
        val buttonRow: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!joined) {
                    _HoverInfo(message = if (joining) "正在加入语音" else "加入语音频道") {
                        Button(
                            modifier = Modifier.testTag("live-control:join"),
                            height = _controlButtonSize.dp,
                            loading = joining,
                            icon = { Icon(Icons.Filled.Call) },
                            onPressed = onJoin,
                        ) {
                            Text("加入")
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                } else {
                    _withControlGaps(controls)
                    Spacer(Modifier.width(10.dp))
                }
                _HoverInfo(message = "收起语音频道") {
                    if (!joined) {
                        Button(
                            modifier = Modifier.testTag("live-control:collapse"),
                            height = _controlButtonSize.dp,
                            icon = { Icon(Icons.Filled.KeyboardArrowUp) },
                            onPressed = onCollapse,
                        ) {
                            Text("收起")
                        }
                    } else {
                        ButtonIcon(
                            modifier = Modifier.testTag("live-control:collapse"),
                            icon = { Icon(Icons.Filled.KeyboardArrowUp) },
                            onPressed = onCollapse,
                            size = _controlButtonSize.dp,
                        )
                    }
                }
            }
        }

        if (musicBoxStrip == null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { buttonRow() }
            return@BoxWithConstraints
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            musicBoxStrip()
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { buttonRow() }
        }
    }
}

@Composable
private fun _withControlGaps(children: List<@Composable () -> Unit>) {
    for ((index, child) in children.withIndex()) {
        if (index > 0) Spacer(Modifier.width(10.dp))
        child()
    }
}

internal const val _hoverVolumePanelHeight: Double = 144.0
private const val _hoverVolumeTrackThickness = 7.0
private const val _hoverVolumeThumbWidth = 26.0
private const val _hoverVolumeThumbHeight = 7.0
private const val _hoverVolumePercentWidth = 40.0
private const val _hoverVolumePercentHeight = 18.0
private const val _hoverVolumePercentGap = 6.0

/**
 * Dart's `_HoverInfo`: a tooltip shown below its target. On Android it is
 * manual (no long-press tooltip), so a hold stays free for the volume panel.
 */
@Composable
internal fun _HoverInfo(message: String, modifier: Modifier = Modifier, child: @Composable () -> Unit) {
    if (message.isEmpty()) {
        Box(modifier, propagateMinConstraints = true) { child() }
        return
    }
    UiTooltip(
        message = message,
        modifier = modifier,
        preferBelow = true,
        verticalOffset = _controlHoverInfoVerticalOffset,
        triggerMode = TooltipTriggerMode.manual,
        content = child,
    )
}

/**
 * A control that reveals a vertical volume slider above itself: on mouse
 * hover, or for 3 s after an Android long press.
 *
 * [childUsesTapGesture] mirrors Flutter's gesture arena: when the child is a
 * tap gesture (not a raw-pointer `PressableSurface`), the long press wins and
 * the release does not also tap it.
 */
@Composable
internal fun _HoverVolumeButton(
    value: Double,
    semanticLabel: String,
    infoMessage: String,
    onChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
    maxValue: Double = 1.0,
    valueFormatter: ((Double) -> String)? = null,
    panelWidth: Double = _controlButtonSize,
    panelHeight: Double = _hoverVolumePanelHeight,
    childUsesTapGesture: Boolean = false,
    child: @Composable () -> Unit,
) = StatefulWidget(
    _HoverVolumeButtonWidget(
        child, value, semanticLabel, infoMessage, onChanged, maxValue, valueFormatter, panelWidth, panelHeight, childUsesTapGesture, modifier,
    ),
) { _HoverVolumeButtonState() }

private class _HoverVolumeButtonWidget(
    val child: @Composable () -> Unit,
    val value: Double,
    val semanticLabel: String,
    val infoMessage: String,
    val onChanged: (Double) -> Unit,
    val maxValue: Double,
    val valueFormatter: ((Double) -> String)?,
    val panelWidth: Double,
    val panelHeight: Double,
    val childUsesTapGesture: Boolean,
    val modifier: Modifier,
)

private class _HoverVolumeButtonState : ViewState<_HoverVolumeButtonWidget>() {
    private var _overlayEntry: OverlayEntry? = null
    private var _hideTimer: CompositionTimer? = null
    private var _targetHovered = false
    private var _overlayHovered = false
    private var _dragging = false
    private var _value = 0.0
    private val _target = _LiveBoundsHandle()
    private var _scope: CoroutineScope? = null
    private var _overlayNeedsBuild = false

    private val _maxValue: Double get() = if (widget.maxValue <= 0) 1.0 else widget.maxValue
    private val _panelWidth: Double get() = widget.panelWidth.coerceAtLeast(1.0)
    private val _panelHeight: Double get() = widget.panelHeight.coerceAtLeast(1.0)

    private fun _normalize(value: Double): Double = value.coerceIn(0.0, _maxValue)

    override fun initState() {
        _value = _normalize(widget.value)
    }

    override fun didUpdateWidget(oldWidget: _HoverVolumeButtonWidget) {
        if (oldWidget.value != widget.value || oldWidget.maxValue != widget.maxValue) {
            _value = _normalize(widget.value)
            _markOverlayNeedsBuild()
        }
    }

    override fun dispose() {
        _hideTimer?.cancel()
        _hideOverlay()
    }

    fun _showOverlay() {
        _hideTimer?.cancel()
        val active = _active
        if (active != null && active !== this) active._hideOverlay()
        _active = this
        val existing = _overlayEntry
        if (existing != null) {
            existing.markNeedsBuild()
            return
        }
        if (!mounted) return
        val overlay = OverlayState.maybeOf(context) ?: return
        val targetRect = _target.rect ?: return
        if (!_target.hasSize) return
        val targetSize = _target.size
        val overlaySize = _target.rootSize
        val targetWidth = (targetRect.right - targetRect.left).value.toDouble()
        val targetHeight = (targetRect.bottom - targetRect.top).value.toDouble()
        val horizontalScale = if (targetSize.width <= 0.dp) 1.0 else targetWidth / targetSize.width.value
        val verticalScale = if (targetSize.height <= 0.dp) 1.0 else targetHeight / targetSize.height.value
        val panelWidth = _panelWidth * horizontalScale
        val panelHeight = _panelHeight * verticalScale
        val maxPanelLeft = (overlaySize.width.value - panelWidth).coerceAtLeast(0.0)
        val maxPanelTop = (overlaySize.height.value - panelHeight).coerceAtLeast(0.0)
        val panelLeft = (targetRect.left.value + (targetWidth - panelWidth) / 2).coerceIn(0.0, maxPanelLeft)
        val panelTop = (targetRect.top.value - panelHeight - 8 * verticalScale).coerceIn(0.0, maxPanelTop)
        val entry =
            OverlayEntry { _ ->
                _Overlay(left = panelLeft.dp, top = panelTop.dp, width = panelWidth.dp, height = panelHeight.dp)
            }
        _overlayEntry = entry
        overlay.insert(entry)
    }

    fun _hideOverlay() {
        _hideTimer?.cancel()
        _hideTimer = null
        if (_active === this) _active = null
        _overlayEntry?.remove()
        _overlayEntry = null
    }

    private fun _scheduleHide(delay: Duration = 90.milliseconds) {
        _hideTimer?.cancel()
        _hideTimer =
            _scope?.startTimer(delay) {
                if (!_targetHovered && !_overlayHovered && !_dragging) _hideOverlay()
            }
    }

    // Dart rebuilds the entry in a post-frame callback; here the rebuild is
    // requested once the composition has been applied.
    private fun _markOverlayNeedsBuild() {
        if (_overlayEntry == null) return
        _overlayNeedsBuild = true
    }

    private fun _setValue(value: Double) {
        val normalized = _normalize(value)
        setState { _value = normalized }
        _overlayEntry?.markNeedsBuild()
        widget.onChanged(normalized)
    }

    private fun _showOverlayForAndroidLongPress() {
        _showOverlay()
        _scheduleHide(delay = 3.seconds)
    }

    @Composable
    private fun _Overlay(left: Dp, top: Dp, width: Dp, height: Dp) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .absoluteOffset(x = left, y = top)
                    .size(width, height)
                    ._liveMouseHover(
                        onEnter = {
                            _overlayHovered = true
                            _showOverlay()
                        },
                        onExit = {
                            _overlayHovered = false
                            _scheduleHide()
                        },
                    ),
                propagateMinConstraints = true,
            ) {
                _HoverVolumePanel(
                    value = _value,
                    maxValue = _maxValue,
                    valueFormatter = widget.valueFormatter,
                    width = width,
                    height = height,
                    semanticLabel = widget.semanticLabel,
                    onChanged = ::_setValue,
                    onChangeStart = {
                        _dragging = true
                        _showOverlay()
                    },
                    onChangeEnd = {
                        _dragging = false
                        _scheduleHide()
                    },
                )
            }
        }
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        val density = LocalDensity.current
        SideEffect {
            if (_overlayNeedsBuild) {
                _overlayNeedsBuild = false
                if (mounted) _overlayEntry?.markNeedsBuild()
            }
        }
        _HoverInfo(message = widget.infoMessage, modifier = widget.modifier) {
            Box(
                Modifier
                    .liveBounds(_target)
                    // Android: a hold opens the panel for a few seconds
                    // (Dart's `GestureDetector(onLongPressStart:)`).
                    .pointerInput(Unit) {
                        val slop = with(density) { kTouchSlop.dp.toPx() }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val ended =
                                withTimeoutOrNull(kLongPressTimeout.inWholeMilliseconds) {
                                    var finished = false
                                    while (!finished) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                        if (!change.pressed || (change.position - down.position).getDistance() > slop) finished = true
                                    }
                                    true
                                }
                            if (ended != null) return@awaitEachGesture
                            _showOverlayForAndroidLongPress()
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                // The long press won the arena: a tap child
                                // must not also see this pointer's release.
                                if (widget.childUsesTapGesture) change.consume()
                                if (!change.pressed) break
                            }
                        }
                    }
                    ._liveMouseHover(
                        onEnter = {
                            _targetHovered = true
                            _showOverlay()
                        },
                        onExit = {
                            _targetHovered = false
                            _scheduleHide()
                        },
                    ),
                propagateMinConstraints = true,
            ) {
                widget.child()
            }
        }
    }

    companion object {
        private var _active: _HoverVolumeButtonState? = null
    }
}

@Composable
private fun _HoverVolumePanel(
    value: Double,
    maxValue: Double,
    width: Dp,
    height: Dp,
    semanticLabel: String,
    onChanged: (Double) -> Unit,
    onChangeStart: (Double) -> Unit,
    onChangeEnd: (Double) -> Unit,
    valueFormatter: ((Double) -> String)? = null,
) {
    val normalized = value.coerceIn(0.0, maxValue)
    val scale = (width.value.toDouble() / _controlButtonSize).coerceIn(0.65, 1.0)
    val shape = RoundedCornerShape(UiRadii.md)
    val percentText = valueFormatter?.invoke(normalized) ?: audioVolumePercentText(normalized)
    Box(
        Modifier
            .semantics {
                contentDescription = semanticLabel
                stateDescription = percentText
            }
            .boxShadow(listOf(BoxShadow(color = Color(0x66080A0D), offset = DpOffset(0.dp, 8.dp), blurRadius = 18.dp)), shape)
            .decoration(UiColors.surface, shape, UiColors.border)
            .testTag("live-volume-panel:$semanticLabel")
            .size(width, height)
            .padding(vertical = (12 * scale).dp),
        contentAlignment = Alignment.Center,
    ) {
        _HoverVolumeSlider(
            value = normalized,
            maxValue = maxValue,
            scale = scale,
            valueFormatter = valueFormatter,
            semanticLabel = semanticLabel,
            onChanged = onChanged,
            onChangeStart = onChangeStart,
            onChangeEnd = onChangeEnd,
        )
    }
}

@Composable
private fun _HoverVolumeSlider(
    value: Double,
    maxValue: Double,
    scale: Double,
    semanticLabel: String,
    onChanged: (Double) -> Unit,
    onChangeStart: (Double) -> Unit,
    onChangeEnd: (Double) -> Unit,
    valueFormatter: ((Double) -> String)? = null,
) = StatefulWidget(
    _HoverVolumeSliderWidget(value, maxValue, scale, semanticLabel, onChanged, onChangeStart, onChangeEnd, valueFormatter),
) { _HoverVolumeSliderState() }

private class _HoverVolumeSliderWidget(
    val value: Double,
    val maxValue: Double,
    val scale: Double,
    val semanticLabel: String,
    val onChanged: (Double) -> Unit,
    val onChangeStart: (Double) -> Unit,
    val onChangeEnd: (Double) -> Unit,
    val valueFormatter: ((Double) -> String)?,
)

private class _HoverVolumeSliderState : ViewState<_HoverVolumeSliderWidget>() {
    private var _pointer: PointerId? = null
    private var _thumbHovered = false
    private var _dragging = false
    private var _interactionValue = 0.0

    private val _maxValue: Double get() = if (widget.maxValue <= 0) 1.0 else widget.maxValue
    private val _scale: Double get() = widget.scale.coerceIn(0.65, 1.0)
    private val _thumbWidth: Double get() = _hoverVolumeThumbWidth * _scale
    private val _thumbHeight: Double get() = _hoverVolumeThumbHeight * _scale
    private val _trackThickness: Double get() = _hoverVolumeTrackThickness * _scale
    private val _percentGap: Double get() = _hoverVolumePercentGap * _scale
    private val _percentWidth: Double get() = _hoverVolumePercentWidth * _scale
    private val _percentHeight: Double get() = _hoverVolumePercentHeight * _scale

    private val _value: Double get() = widget.value.coerceIn(0.0, _maxValue)
    private val _fraction: Double get() = (_value / _maxValue).coerceIn(0.0, 1.0)

    override fun initState() {
        _interactionValue = _value
    }

    private fun _fractionFromPosition(dy: Double, height: Double): Double {
        val travel = height - _thumbHeight
        if (travel <= 0) return 0.0
        return (1 - (dy - _thumbHeight / 2) / travel).coerceIn(0.0, 1.0)
    }

    private fun _emitFromPosition(dy: Double, height: Double) {
        val fraction = _fractionFromPosition(dy, height)
        val value = fraction * _maxValue
        _interactionValue = value
        widget.onChanged(value)
    }

    fun _handlePointerDown(id: PointerId, dy: Double, height: Double) {
        if (_pointer != null) return
        _pointer = id
        _interactionValue = _value
        setState { _dragging = true }
        widget.onChangeStart(_interactionValue)
        _emitFromPosition(dy, height)
    }

    fun _handlePointerMove(id: PointerId, dy: Double, height: Double) {
        if (id != _pointer) return
        _emitFromPosition(dy, height)
    }

    fun _handlePointerUp(id: PointerId) {
        if (id != _pointer) return
        _pointer = null
        setState { _dragging = false }
        widget.onChangeEnd(_interactionValue)
    }

    override fun didUpdateWidget(oldWidget: _HoverVolumeSliderWidget) {
        if (!_dragging) _interactionValue = _value
    }

    @Composable
    override fun build() {
        val percentText = widget.valueFormatter?.invoke(_value) ?: audioVolumePercentText(_value)
        val showPercent = _thumbHovered || _dragging
        val density = LocalDensity.current
        val label = widget.semanticLabel
        val thumbWidth = _thumbWidth
        val thumbHeight = _thumbHeight
        val trackThickness = _trackThickness
        val percentGap = _percentGap
        val percentWidth = _percentWidth
        val percentHeight = _percentHeight
        val fraction = _fraction
        Layout(
            modifier =
                Modifier
                    .testTag("live-volume-slider:$label")
                    .width(thumbWidth.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val heightDp = size.height / density.density.toDouble()
                                for (change in event.changes) {
                                    val dy = change.position.y / density.density.toDouble()
                                    when (event.type) {
                                        PointerEventType.Press ->
                                            if (change.pressed && !change.previousPressed) _handlePointerDown(change.id, dy, heightDp)
                                        PointerEventType.Move -> _handlePointerMove(change.id, dy, heightDp)
                                        PointerEventType.Release ->
                                            if (!change.pressed && change.previousPressed) _handlePointerUp(change.id)
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    },
            content = {
                // Track.
                Box(Modifier.decoration(UiColors.surfacePressed, RoundedCornerShape((trackThickness / 2).dp)))
                // Fill.
                Box(
                    Modifier
                        .testTag("live-volume-fill:$label")
                        .decoration(
                            UiColors.accent,
                            RoundedCornerShape(bottomStart = (trackThickness / 2).dp, bottomEnd = (trackThickness / 2).dp),
                        ),
                )
                if (showPercent) {
                    Box(
                        Modifier
                            .testTag("live-volume-percent:$label")
                            .decoration(Color.White, RoundedCornerShape(UiRadii.sm)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(percentText, style = UiTypography.label.copyWith(color = Color(0xDD000000), fontSize = 10 * _scale))
                    }
                }
                // Thumb.
                Box(
                    Modifier
                        .testTag("live-volume-thumb:$label")
                        ._liveMouseHover(onEnter = { setState { _thumbHovered = true } }, onExit = { setState { _thumbHovered = false } })
                        .decoration(UiColors.text, RoundedCornerShape((thumbHeight / 2).dp)),
                )
            },
        ) { measurables, constraints ->
            val heightPx = constraints.maxHeight
            val scaleFactor = density.density.toDouble()
            val height = heightPx / scaleFactor
            fun px(value: Double): Int = (value * scaleFactor).roundToInt()
            val travel = (height - thumbHeight).coerceAtLeast(0.0)
            val thumbBottom = travel * fraction
            val labelBottom = (thumbBottom + (thumbHeight - percentHeight) / 2).coerceIn(0.0, (height - percentHeight).coerceAtLeast(0.0))
            val trackLeft = (thumbWidth - trackThickness) / 2
            val track = measurables[0].measure(Constraints.fixed(px(trackThickness), heightPx))
            val fill = measurables[1].measure(Constraints.fixed(px(trackThickness), px(thumbBottom)))
            val percent = if (showPercent) measurables[2].measure(Constraints.fixed(px(percentWidth), px(percentHeight))) else null
            val thumb = measurables.last().measure(Constraints.fixed(px(thumbWidth), px(thumbHeight)))
            layout(constraints.maxWidth, heightPx) {
                track.place(px(trackLeft), 0)
                fill.place(px(trackLeft), heightPx - px(thumbBottom))
                percent?.place(px(thumbWidth + percentGap), heightPx - px(labelBottom) - px(percentHeight))
                thumb.place(0, heightPx - px(thumbBottom) - px(thumbHeight))
            }
        }
    }
}

/**
 * The now-playing strip embedded inline above the live control bar: a small
 * spinning vinyl, the current track, transport controls, and a button that
 * expands the full search + queue panel.
 */
@Composable
private fun _InlineMusicBox(
    state: MusicBoxState,
    expanded: Boolean,
    onTogglePlayback: () -> Unit,
    onSkip: () -> Unit,
    onToggleExpand: () -> Unit,
    controller: MusicBoxController?,
    roomId: String?,
    onStateChanged: ((MusicBoxState) -> Unit)?,
    currentUser: CurrentUser,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    userProfileActionBuilder: UserProfileActionBuilder?,
    modifier: Modifier = Modifier,
) {
    val current = state.currentItem
    val spinning = musicBoxRecordSpinning(state)
    val transport = musicBoxPrimaryTransport(state)
    val hasQueue = state.queue.isNotEmpty()
    val isPause = transport == MusicBoxTransportAction.pause

    Box(modifier.testTag("live-control:music-inline").height(_controlButtonSize.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Drop the skip button once the strip narrows so the title/artist
            // can keep shrinking instead of the whole strip bottoming out at
            // the full button-row width.
            val showSkip = maxWidth >= 175.dp
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                _VinylRecord(spinning = spinning, label = current?.title, size = 26.0)
                Spacer(Modifier.width(8.dp))
                // Dart's Expanded: the anchor gets the slot's tight width.
                Box(Modifier.weight(1f), propagateMinConstraints = true) {
                    if (current == null) {
                        _InlineMusicBoxIdentity(current = null)
                    } else {
                        HoverCardAnchor(
                            modifier = Modifier.testTag("live-control:music-current-card"),
                            resetKey =
                                listOf(
                                    current.id,
                                    current.status,
                                    current.requestedBy?.avatarUrl,
                                    current.requestedBy?.avatarLabel,
                                    state.activeSource.type,
                                    state.activeSource.id,
                                    state.activeSource.name,
                                ),
                            cardWidth = 310.dp,
                            cardBuilder = { _ ->
                                _MusicBoxSongCardQueue(
                                    item = current,
                                    isCurrent = true,
                                    queueSongCount = state.queue.size,
                                    controller = controller,
                                    roomId = roomId,
                                    activeSource = state.activeSource,
                                    onStateChanged = onStateChanged,
                                    currentUser = currentUser,
                                    onResolveUserProfile = onResolveUserProfile,
                                    onResolveRoomProfile = onResolveRoomProfile,
                                    onEnterCommonRoom = onEnterCommonRoom,
                                    userProfileActionBuilder = userProfileActionBuilder,
                                )
                            },
                        ) {
                            _InlineMusicBoxIdentity(current = current)
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                _HoverInfo(message = if (isPause) "暂停音乐" else "播放音乐") {
                    ButtonIcon(
                        modifier = Modifier.testTag("live-control:music-toggle"),
                        icon = { Icon(if (isPause) Icons.Filled.Pause else Icons.Filled.PlayArrow) },
                        tone = ButtonTone.primary,
                        onPressed = if (hasQueue) onTogglePlayback else null,
                        size = 30.dp,
                    )
                }
                if (showSkip) {
                    Spacer(Modifier.width(4.dp))
                    _HoverInfo(message = "下一首") {
                        ButtonIcon(
                            modifier = Modifier.testTag("live-control:music-skip"),
                            icon = { Icon(Icons.Filled.SkipNext) },
                            onPressed = if (hasQueue) onSkip else null,
                            size = 30.dp,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                _HoverInfo(message = if (expanded) "收起音乐面板" else "打开音乐面板") {
                    ButtonIcon(
                        modifier = Modifier.testTag("live-control:music-queue"),
                        icon = { Icon(Icons.Filled.LibraryMusic) },
                        selected = expanded,
                        onPressed = onToggleExpand,
                        size = 30.dp,
                    )
                }
            }
        }
        // A hairline progress track pinned to the bottom edge of the strip,
        // spanning its full width — keeps the title/artist lines intact
        // while still reading playback position at a glance.
        _InlineProgress(state = state, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth())
    }
}

@Composable
private fun _InlineMusicBoxIdentity(current: MusicBoxQueueItem?) {
    Column(Modifier.fillMaxWidth()) {
        OverflowMarqueeText(
            modifier = Modifier.testTag("live-control:music-title"),
            trackKey = "live-control:music-title-marquee-track",
            text = current?.title ?: "未在播放",
            style = uiTextStyle(color = if (current == null) UiColors.textMuted else UiColors.text, fontSize = 12.0, fontWeight = FontWeight.W600),
        )
        Text(
            if (current?.artist?.isNotEmpty() == true) current.artist else "点一首歌开始播放",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = uiTextStyle(color = UiColors.textSecondary, fontSize = 10.0, fontWeight = FontWeight.W600),
        )
    }
}

/**
 * A hairline server-authoritative progress track that hugs the bottom edge of
 * the inline music box (Material's determinate `LinearProgressIndicator`).
 */
@Composable
private fun _InlineProgress(state: MusicBoxState, modifier: Modifier = Modifier) {
    val fraction = musicBoxProgress(state).fraction.toFloat()
    Box(
        modifier
            .height(3.dp)
            .drawBehind {
                drawRect(UiColors.surfacePressed)
                drawRect(UiColors.accent, size = size.copy(width = size.width * fraction))
            },
    )
}
