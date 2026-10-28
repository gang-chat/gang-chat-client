package com.gangchat.client.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.AudioDeviceInfo
import com.gangchat.client.app.activeAudioLevelSegmentCount
import com.gangchat.client.app.audioDeviceBusy
import com.gangchat.client.app.audioDeviceInfoId
import com.gangchat.client.app.audioDeviceInfoKind
import com.gangchat.client.app.audioDeviceInfoLabel
import com.gangchat.client.app.audioDeviceLabels
import com.gangchat.client.app.audioLevelSegmentCount
import com.gangchat.client.app.boostedAudioVolumePercentText
import com.gangchat.client.app.isSameAudioDevice
import com.gangchat.client.live.androidScreenShareFrameRateCap
import com.gangchat.client.live.normalizedScreenShareFrameRate
import com.gangchat.client.live.normalizedScreenShareMaxHeight
import com.gangchat.client.live.screenShareFrameRateLabel
import com.gangchat.client.live.screenShareFrameRateOptions
import com.gangchat.client.live.screenShareHeightOptions
import com.gangchat.client.live.screenShareResolutionLabel
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.PressableSurface
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiSwitch
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.decoration

@Composable
internal fun _DeviceSection(
    title: String,
    icon: ImageVector,
    devices: List<AudioDeviceInfo>,
    selectedDevice: AudioDeviceInfo?,
    busyDeviceId: String?,
    emptyText: String,
    fallbackLabel: String,
    onSelect: (AudioDeviceInfo) -> Unit,
) {
    val labels =
        audioDeviceLabels(
            devices,
            fallbackLabel = fallbackLabel,
            labelOf = ::audioDeviceInfoLabel,
            deviceIdOf = ::audioDeviceInfoId,
        )
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, color = _cyan, size = 18.dp)
            Spacer(Modifier.width(9.dp))
            Text(title, style = uiTextStyle(color = _textPrimary, fontSize = 15.0, fontWeight = FontWeight.W600))
        }
        Spacer(Modifier.height(12.dp))
        if (devices.isEmpty()) {
            _EmptyDeviceRow(text = emptyText)
        } else {
            Column(Modifier.fillMaxWidth()) {
                for ((index, device) in devices.withIndex()) {
                    _DeviceRow(
                        device = device,
                        label = labels[index],
                        selected =
                            isSameAudioDevice(
                                selectedDevice,
                                device,
                                kindOf = ::audioDeviceInfoKind,
                                deviceIdOf = ::audioDeviceInfoId,
                            ),
                        busy =
                            audioDeviceBusy(
                                device,
                                busyDeviceId,
                                kindOf = ::audioDeviceInfoKind,
                                deviceIdOf = ::audioDeviceInfoId,
                            ),
                        onTap = { onSelect(device) },
                    )
                }
            }
        }
    }
}

/** Test tag of the volume slider in the panel titled [title] (Dart finds them as `find.byType(Slider)`). */
fun settingsVolumeSliderTag(title: String) = "settings-volume-slider:$title"

/** Test tag of the level meter in the panel titled [title]. */
fun settingsLevelMeterTag(title: String) = "settings-level-meter:$title"

/** Test tag of the 增强 switch in the panel titled [title]. */
fun settingsVolumeBoostTag(title: String) = "settings-volume-boost:$title"

@Composable
internal fun _AudioControlPanel(
    title: String,
    icon: ImageVector,
    volume: Double,
    level: Double,
    testing: Boolean,
    testTooltip: String,
    disabled: Boolean,
    onVolumeChanged: (Double) -> Unit,
    onToggleTest: () -> Unit,
    testDisabled: Boolean = false,
    /** The 增强 switch (gain × 1.5); [onBoostChanged] null when the volume cannot be boosted. */
    boost: Boolean = false,
    onBoostChanged: ((Boolean) -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, color = _cyan, size = 18.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = uiTextStyle(color = _textPrimary, fontSize = 15.0, fontWeight = FontWeight.W600),
            )
            if (onBoostChanged != null) {
                Text(
                    "增强 +50%",
                    softWrap = false,
                    style =
                        uiTextStyle(
                            color = if (boost) _textPrimary else _textSecondary,
                            fontSize = 12.0,
                            fontWeight = FontWeight.W600,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                UiSwitch(
                    value = boost,
                    onChanged = if (disabled) null else onBoostChanged,
                    tooltip = "在当前音量上再放大 50%",
                    modifier =
                        Modifier
                            .testTag(settingsVolumeBoostTag(title))
                            .semantics { contentDescription = "${title}增强" },
                )
                Spacer(Modifier.width(14.dp))
            }
            Text(
                boostedAudioVolumePercentText(volume, boost),
                modifier = Modifier.widthIn(min = 38.dp),
                textAlign = TextAlign.End,
                softWrap = false,
                style = uiTextStyle(color = _textSecondary, fontSize = 13.0, fontWeight = FontWeight.W600),
            )
        }
        Spacer(Modifier.height(8.dp))
        _VolumeSlider(
            value = volume,
            onChanged = if (disabled) null else onVolumeChanged,
            modifier = Modifier.testTag(settingsVolumeSliderTag(title)),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(96.dp)) {
                Button(
                    onPressed = if (disabled || testDisabled) null else onToggleTest,
                    tooltip = testTooltip,
                    height = 42.dp,
                    width = 96.dp,
                    tone = if (testing) ButtonTone.primary else ButtonTone.neutral,
                    selected = testing,
                    padding = EdgeInsets.symmetric(horizontal = 8.dp),
                ) {
                    Text(if (testing) "停止测试" else "测试", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                _LevelMeter(level = level, active = testing, modifier = Modifier.testTag(settingsLevelMeterTag(title)))
            }
        }
    }
}

/**
 * The Material `Slider` under the panel's `SliderTheme` (track 4 dp, lime
 * active track 2 dp taller, a 20 dp text-coloured thumb, a 48 dp tall touch
 * area with the track inset by the 24 dp overlay radius, and the translucent
 * overlay while dragging). Like Flutter's slider it reports its value and a
 * set-value action to accessibility.
 */
@Composable
private fun _VolumeSlider(value: Double, onChanged: ((Double) -> Unit)?, modifier: Modifier = Modifier) {
    val enabled = onChanged != null
    val activeColor = if (enabled) _cyan else UiColors.text.copy(alpha = 0.38f)
    val inactiveColor = if (enabled) _borderColor else UiColors.text.copy(alpha = 0.12f)
    val thumbColor = if (enabled) _textPrimary else UiColors.text.copy(alpha = 0.38f)
    val overlayColor = _cyan.copy(alpha = 0.14f)
    val dragging = remember { mutableStateOf(false) }
    // The drag detector outlives rebuilds; it reads the latest callback.
    val currentOnChanged = androidx.compose.runtime.rememberUpdatedState(onChanged)
    val clamped = value.coerceIn(0.0, 1.0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(clamped.toFloat(), 0f..1f)
                if (onChanged != null) {
                    setProgress { target ->
                        onChanged(target.toDouble().coerceIn(0.0, 1.0))
                        true
                    }
                }
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val inset = 24.dp.toPx()
                    fun emit(x: Float) {
                        val travel = size.width - inset * 2
                        if (travel <= 0f) return
                        currentOnChanged.value?.invoke(((x - inset) / travel).toDouble().coerceIn(0.0, 1.0))
                    }
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            when (event.type) {
                                PointerEventType.Press -> {
                                    dragging.value = true
                                    emit(change.position.x)
                                    change.consume()
                                }
                                PointerEventType.Move ->
                                    if (dragging.value) {
                                        emit(change.position.x)
                                        change.consume()
                                    }
                                PointerEventType.Release -> dragging.value = false
                                else -> Unit
                            }
                        }
                    }
                },
        ) {
            val inset = 24.dp.toPx()
            val trackHeight = 4.dp.toPx()
            val activeHeight = trackHeight + 2.dp.toPx()
            val centerY = size.height / 2
            val trackLeft = inset
            val trackRight = size.width - inset
            val thumbX = trackLeft + (trackRight - trackLeft) * clamped.toFloat()
            drawRoundRect(
                inactiveColor,
                topLeft = Offset(thumbX, centerY - trackHeight / 2),
                size = Size((trackRight - thumbX).coerceAtLeast(0f), trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2),
            )
            drawRoundRect(
                activeColor,
                topLeft = Offset(trackLeft - activeHeight / 2, centerY - activeHeight / 2),
                size = Size((thumbX - trackLeft + activeHeight / 2).coerceAtLeast(0f), activeHeight),
                cornerRadius = CornerRadius(activeHeight / 2),
            )
            if (dragging.value) drawCircle(overlayColor, radius = 24.dp.toPx(), center = Offset(thumbX, centerY))
            drawCircle(thumbColor, radius = 10.dp.toPx(), center = Offset(thumbX, centerY))
        }
    }
}

@Composable
internal fun _LevelMeter(level: Double, active: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .decoration(_primaryDark)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        val segmentCount = audioLevelSegmentCount(maxWidth.value.toDouble())
        val activeCount = activeAudioLevelSegmentCount(level = level, active = active, segmentCount = segmentCount)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until segmentCount) {
                val color by animateColorAsState(
                    if (i < activeCount) _cyan else UiColors.surfaceHover,
                    tween(90),
                    label = "levelSegment",
                )
                Box(Modifier.size(width = 5.dp, height = 22.dp).decoration(color, RoundedCornerShape(3.dp)))
            }
        }
    }
}

@Composable
internal fun _ScreenShareQualitySection(
    selectedHeight: Int,
    selectedFrameRate: Int,
    onHeightSelected: (Int) -> Unit,
    onFrameRateSelected: (Int) -> Unit,
    remoteUnavailable: Boolean = false,
) {
    val selectedResolution = normalizedScreenShareMaxHeight(selectedHeight)
    val platformFrameRateCap = androidScreenShareFrameRateCap
    val selectedFps = normalizedScreenShareFrameRate(selectedFrameRate, maxFrameRate = platformFrameRateCap)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ScreenShare, color = _cyan, size = 18.dp)
            Spacer(Modifier.width(9.dp))
            Text("屏幕共享画质", style = uiTextStyle(color = _textPrimary, fontSize = 15.0, fontWeight = FontWeight.W600))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "分辨率、帧率和码率会一起调整；较低档位可以显著节省上行带宽。",
            style = uiTextStyle(color = _textSecondary, fontSize = 13.0, height = 1.4),
        )
        Spacer(Modifier.height(12.dp))
        if (remoteUnavailable) {
            _SettingsEmptyState(text = "该设置仅保存在用户设备，无法远程读取")
        } else {
            Column(Modifier.fillMaxWidth()) {
                Text("分辨率", style = uiTextStyle(color = _textSecondary, fontSize = 13.0, fontWeight = FontWeight.W600))
                Spacer(Modifier.height(6.dp))
                for (height in screenShareHeightOptions) {
                    _ScreenShareResolutionRow(
                        label = screenShareResolutionLabel(height),
                        selected = height == selectedResolution,
                        onTap = { onHeightSelected(height) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("帧率", style = uiTextStyle(color = _textSecondary, fontSize = 13.0, fontWeight = FontWeight.W600))
                Spacer(Modifier.height(6.dp))
                for (frameRate in screenShareFrameRateOptions) {
                    if (frameRate <= platformFrameRateCap) {
                        _ScreenShareResolutionRow(
                            label = screenShareFrameRateLabel(frameRate),
                            selected = frameRate == selectedFps,
                            unselectedIcon = Icons.Outlined.Speed,
                            onTap = { onFrameRateSelected(frameRate) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun _ScreenShareResolutionRow(
    label: String,
    selected: Boolean,
    onTap: () -> Unit,
    unselectedIcon: ImageVector = Icons.Outlined.HighQuality,
) {
    PressableSurface(
        onPressed = onTap,
        height = 50.dp,
        selected = selected,
        backgroundColor = _primaryDarkLow,
        selectedBackgroundColor = UiColors.selected,
        pressedBackgroundColor = _primaryDark,
        borderColor = _borderColor,
        selectedBorderColor = UiColors.selectedBorder,
        padding = EdgeInsets.symmetric(horizontal = 14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = uiTextStyle(color = if (selected) _textPrimary else _textSecondary, fontSize = 14.0, fontWeight = FontWeight.W600),
            )
            if (selected) {
                Icon(Icons.Filled.Check, color = _cyan, size = 18.dp)
            } else {
                Icon(unselectedIcon, color = _textMuted, size = 18.dp)
            }
        }
    }
}

@Composable
private fun _DeviceRow(
    device: AudioDeviceInfo,
    label: String,
    selected: Boolean,
    busy: Boolean,
    onTap: () -> Unit,
) {
    PressableSurface(
        onPressed = if (busy) null else onTap,
        height = 50.dp,
        selected = selected,
        backgroundColor = _primaryDarkLow,
        selectedBackgroundColor = UiColors.selected,
        pressedBackgroundColor = _primaryDark,
        borderColor = _borderColor,
        selectedBorderColor = UiColors.selectedBorder,
        padding = EdgeInsets.symmetric(horizontal = 14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = uiTextStyle(color = if (selected) _textPrimary else _textSecondary, fontSize = 14.0, fontWeight = FontWeight.W600),
            )
            if (busy) {
                _SettingsProgressIndicator(size = 16.dp, strokeWidth = 2.dp)
            } else if (selected) {
                Icon(Icons.Filled.Check, color = _cyan, size = 18.dp)
            } else {
                Icon(
                    if (device.kind == "audioinput") Icons.Filled.MicNone else Icons.AutoMirrored.Filled.VolumeUp,
                    color = _textMuted,
                    size = 18.dp,
                )
            }
        }
    }
}

@Composable
private fun _EmptyDeviceRow(text: String) {
    Box(Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.CenterStart) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp),
            style = uiTextStyle(color = _textMuted, fontSize = 13.0, fontWeight = FontWeight.W600),
        )
    }
}
