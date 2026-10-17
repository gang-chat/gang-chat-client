package com.gangchat.client.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.AppUpdatePlatform
import com.gangchat.client.app.AutoUpdatePromptStore
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.appVersionLabel
import com.gangchat.client.app.compareAppVersions
import com.gangchat.client.app.gangChatClientVersion
import com.gangchat.client.app.releaseNotesLabel
import com.gangchat.client.app.releaseTimeLabel
import com.gangchat.client.config.HostPlatform
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.ResponsiveDialogActionBar
import com.gangchat.client.ui.SettingsCard
import com.gangchat.client.ui.SettingsList
import com.gangchat.client.ui.SettingsScaffold
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Scaffold
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import kotlinx.coroutines.CancellationException
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration

typealias AppUpdateAvailableCallback = (update: AvailableAppUpdate) -> Unit

/** The default prompt store (Dart's canonical `const LocalAutoUpdatePromptStore()`). */
private val _defaultAutoUpdatePromptStore: AutoUpdatePromptStore = LocalAutoUpdatePromptStore()

/**
 * Checks the release bucket once [child] is shown and reports a newer
 * release through [onUpdateAvailable] (once per release asset, unless the
 * automatic prompt is off or that version was ignored). It never covers
 * [child]: the app shows the update in settings ([AppUpdatePage]).
 *
 * PORT: Dart's `windowController` (unused here), the `sparkleUpdater` and
 * the macOS Sparkle branch are desktop-only and not ported.
 */
@Composable
fun AppUpdateGate(
    releaseBucketUrl: String,
    child: @Composable () -> Unit,
    currentVersion: String = gangChatClientVersion,
    autoUpdatePromptStore: AutoUpdatePromptStore = _defaultAutoUpdatePromptStore,
    updateService: ReleaseUpdateService? = null,
    platformOverride: AppUpdatePlatform? = null,
    onUpdateAvailable: AppUpdateAvailableCallback? = null,
) = StatefulWidget(
    AppUpdateGateWidget(
        child, currentVersion, releaseBucketUrl, autoUpdatePromptStore, updateService, platformOverride, onUpdateAvailable,
    ),
) { _AppUpdateGateState() }

class AppUpdateGateWidget(
    val child: @Composable () -> Unit,
    val currentVersion: String,
    val releaseBucketUrl: String,
    val autoUpdatePromptStore: AutoUpdatePromptStore,
    val updateService: ReleaseUpdateService?,
    val platformOverride: AppUpdatePlatform?,
    val onUpdateAvailable: AppUpdateAvailableCallback?,
)

private class _AppUpdateGateState : ViewState<AppUpdateGateWidget>() {
    private var _checking = false
    private var _notifiedAssetKey: String? = null

    private val _updateService: ReleaseUpdateService
        get() = widget.updateService ?: ReleaseUpdateService()

    override fun initState() {
        unawaited { _checkForUpdate() }
    }

    override fun didUpdateWidget(oldWidget: AppUpdateGateWidget) {
        if (oldWidget.releaseBucketUrl != widget.releaseBucketUrl ||
            oldWidget.currentVersion != widget.currentVersion ||
            oldWidget.updateService !== widget.updateService
        ) {
            _notifiedAssetKey = null
            unawaited { _checkForUpdate() }
        }
    }

    private suspend fun _checkForUpdate() {
        if (_checking) return
        val platform = _currentPlatform() ?: return

        // PORT: Dart's first `await` always yields, so the check (and the
        // callback into the parent) never runs inside the build that started
        // it; with a store that answers without suspending it would here.
        delayed(Duration.ZERO)
        try {
            val enabled = widget.autoUpdatePromptStore.read()
            if (!enabled) return
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            return
        }
        if (!mounted) return

        setState { _checking = true }
        try {
            val update =
                _updateService.checkForUpdate(
                    bucketUrl = widget.releaseBucketUrl,
                    currentVersion = widget.currentVersion,
                    platform = platform,
                )
            if (!mounted) return
            setState { _checking = false }
            if (update == null || update.asset.key == _notifiedAssetKey) return
            val ignoredVersion = widget.autoUpdatePromptStore.readIgnoredVersion()
            if (!mounted) return
            if (ignoredVersion != null && compareAppVersions(update.latestVersion, ignoredVersion) <= 0) {
                return
            }
            _notifiedAssetKey = update.asset.key
            widget.onUpdateAvailable?.invoke(update)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState { _checking = false }
        }
    }

    private fun _currentPlatform(): AppUpdatePlatform? {
        val override = widget.platformOverride
        if (override != null) return override
        return appUpdatePlatformForOperatingSystem(HostPlatform.operatingSystem)
    }

    @Composable
    override fun build() = widget.child()
}

/** Test tag of the release time value's scale-down box (Dart's `FittedBox`). */
const val appUpdateReleaseTimeFitTag = "app-update-release-time-fit"

/**
 * The "发现新版本" page shown in settings: version details, release notes,
 * download progress, the last error, and the ignore / download actions.
 */
@Composable
fun AppUpdatePage(
    update: AvailableAppUpdate,
    checking: Boolean,
    downloading: Boolean,
    downloadedBytes: Long,
    onDownload: () -> Unit,
    onBack: () -> Unit,
    onIgnoreVersion: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    downloadTotalBytes: Long? = null,
    error: String? = null,
    wrapInScaffold: Boolean = false,
) {
    val total = downloadTotalBytes
    val downloadProgress: Double? =
        if (total == null || total <= 0) null else (downloadedBytes.toDouble() / total).coerceIn(0.0, 1.0)

    val page: @Composable () -> Unit = {
        SettingsScaffold(
            icon = Icons.Outlined.SystemUpdateAlt,
            iconColor = UiColors.controlAccent,
            title = "发现新版本",
            onBack = if (downloading) null else onBack,
            headerAction = {
                ButtonIcon(
                    tooltip = "重新检查",
                    icon = { Icon(Icons.Filled.Refresh) },
                    loading = checking,
                    onPressed = if (checking || downloading) null else onRefresh,
                    size = 38.dp,
                )
            },
            modifier = if (wrapInScaffold) Modifier else modifier,
            body = {
                val children = mutableListOf<@Composable () -> Unit>()
                children.add {
                    val cardChildren = mutableListOf<@Composable () -> Unit>()
                    cardChildren.add {
                        _VersionLine(label = "当前版本", value = appVersionLabel(update.currentVersion))
                    }
                    cardChildren.add {
                        _VersionLine(label = "最新版本", value = appVersionLabel(update.latestVersion), accent = true)
                    }
                    cardChildren.add {
                        _VersionLine(
                            label = "发行时间",
                            value = releaseTimeLabel(update.asset.releasedAt),
                            scaleValueToFit = true,
                        )
                    }
                    cardChildren.add { _VersionLog(value = releaseNotesLabel(update.releaseNotes)) }
                    if (downloading) {
                        cardChildren.add { Spacer(Modifier.height(2.dp)) }
                        cardChildren.add {
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(UiRadii.sm))) {
                                _LinearProgressIndicator(
                                    value = downloadProgress,
                                    minHeight = 8.dp,
                                    color = UiColors.controlAccent,
                                    backgroundColor = UiColors.surfacePressed,
                                )
                            }
                        }
                        cardChildren.add {
                            Text(
                                if (downloadProgress == null) {
                                    "正在下载更新"
                                } else {
                                    "正在下载更新 ${(downloadProgress * 100).roundToInt()}%"
                                },
                                style = UiTypography.label,
                            )
                        }
                    }
                    SettingsCard(
                        title = "版本更新",
                        trailing = { _UpdateBadge(version = update.latestVersion) },
                        children = cardChildren,
                    )
                }
                if (error != null) {
                    children.add {
                        SettingsCard(
                            title = "更新失败",
                            danger = true,
                            children =
                                listOf {
                                    Text(error, style = UiTypography.label.copyWith(color = UiColors.danger, height = 1.5))
                                },
                        )
                    }
                }
                children.add {
                    _UpdateActions(
                        downloading = downloading,
                        onDownload = onDownload,
                        onIgnoreVersion = onIgnoreVersion,
                    )
                }
                SettingsList(children = children)
            },
        )
    }
    if (!wrapInScaffold) {
        page()
        return
    }
    Scaffold(backgroundColor = UiColors.surfaceLow, modifier = modifier) { page() }
}

@Composable
private fun _VersionLine(label: String, value: String, accent: Boolean = false, scaleValueToFit: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(86.dp)) { Text(label, style = UiTypography.label) }
        Box(Modifier.weight(1f)) {
            if (scaleValueToFit) _scaledValue(value, accent) else _valueText(value, accent)
        }
    }
}

@Composable
private fun _scaledValue(value: String, accent: Boolean) {
    // Align(centerLeft) > FittedBox(scaleDown, centerLeft).
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        _ScaleDown(Modifier.testTag(appUpdateReleaseTimeFitTag)) {
            _valueText(value, accent, overflow = TextOverflow.Visible)
        }
    }
}

@Composable
private fun _valueText(value: String, accent: Boolean, overflow: TextOverflow = TextOverflow.Ellipsis) {
    Text(
        value,
        maxLines = 1,
        softWrap = false,
        overflow = overflow,
        style =
            uiTextStyle(
                color = if (accent) UiColors.accent else UiColors.text,
                fontSize = 15.0,
                fontWeight = FontWeight.W600,
                letterSpacing = 0.0,
            ),
    )
}

/**
 * Flutter's `FittedBox(fit: BoxFit.scaleDown, alignment: centerLeft)`: lays
 * the child out without width limits and scales it down (never up) to fit,
 * shrinking its own size to the scaled child.
 */
@Composable
private fun _ScaleDown(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints())
        val width = placeable.width
        val height = placeable.height
        val scale =
            if (width == 0 || height == 0) {
                1f
            } else {
                min(1f, min(constraints.maxWidth.toFloat() / width, constraints.maxHeight.toFloat() / height))
            }
        val boxWidth = (width * scale).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val boxHeight = (height * scale).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(boxWidth, boxHeight) {
            placeable.placeWithLayer(0, ((boxHeight - height * scale) / 2).roundToInt()) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

@Composable
private fun _VersionLog(value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(86.dp)) { Text("版本日志", style = UiTypography.label) }
        Box(Modifier.weight(1f)) {
            Text(
                value,
                style =
                    uiTextStyle(
                        color = UiColors.text,
                        fontSize = 14.0,
                        fontWeight = FontWeight.W500,
                        height = 1.5,
                        letterSpacing = 0.0,
                    ),
            )
        }
    }
}

@Composable
private fun _UpdateBadge(version: String) {
    Box(
        Modifier
            .decoration(UiColors.selected, RoundedCornerShape(UiRadii.sm), UiColors.selectedBorder)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            appVersionLabel(version),
            style = UiTypography.label.copyWith(color = UiColors.accent, fontWeight = FontWeight.W700),
        )
    }
}

@Composable
private fun _UpdateActions(downloading: Boolean, onDownload: () -> Unit, onIgnoreVersion: () -> Unit) {
    ResponsiveDialogActionBar(
        expanded = true,
        actions =
            listOf(
                ResponsiveDialogAction(
                    label = "忽略此版本",
                    icon = Icons.Outlined.NotificationsOff,
                    onPressed = if (downloading) null else onIgnoreVersion,
                ),
                ResponsiveDialogAction(
                    label = "下载新版本",
                    icon = Icons.Outlined.DownloadForOffline,
                    tone = ButtonTone.primary,
                    loading = downloading,
                    onPressed = if (downloading) null else onDownload,
                ),
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

// Material's indeterminate linear progress (Flutter's
// `_LinearProgressIndicatorState`): two bars whose head and tail follow
// these intervals of an 1800 ms cycle.
private const val _kIndeterminateLinearDuration = 1800
private val _line1Head = _Interval(0f, 750f / 1800f, 0.2f, 0.0f, 0.8f, 1.0f)
private val _line1Tail = _Interval(333f / 1800f, (333f + 750f) / 1800f, 0.4f, 0.0f, 1.0f, 1.0f)
private val _line2Head = _Interval(1000f / 1800f, (1000f + 567f) / 1800f, 0.0f, 0.0f, 0.65f, 1.0f)
private val _line2Tail = _Interval(1267f / 1800f, (1267f + 533f) / 1800f, 0.10f, 0.0f, 0.45f, 1.0f)

/** Flutter's `Interval(begin, end, curve: Cubic(a, b, c, d))`. */
private class _Interval(val begin: Float, val end: Float, a: Float, b: Float, c: Float, d: Float) {
    private val curve = androidx.compose.animation.core.CubicBezierEasing(a, b, c, d)

    fun transform(t: Float): Float {
        val local = ((t - begin) / (end - begin)).coerceIn(0f, 1f)
        if (local == 0f || local == 1f) return local
        return curve.transform(local)
    }
}

/**
 * Flutter's Material `LinearProgressIndicator(value:, minHeight:, color:,
 * backgroundColor:)` (the 2021 style the app's theme uses): a full-width
 * track with the value bar from the start; a null [value] animates.
 */
@Composable
private fun _LinearProgressIndicator(value: Double?, minHeight: Dp, color: Color, backgroundColor: Color) {
    val modifier = Modifier.fillMaxWidth().height(minHeight)
    if (value != null) {
        Canvas(modifier) {
            drawRect(backgroundColor)
            _drawBar(color, 0f, value.toFloat().coerceIn(0f, 1f) * size.width)
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "linearProgress")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(_kIndeterminateLinearDuration, easing = LinearEasing), RepeatMode.Restart),
        label = "linearProgressValue",
    )
    Canvas(modifier) {
        drawRect(backgroundColor)
        val x1 = size.width * _line1Tail.transform(t)
        val width1 = size.width * _line1Head.transform(t) - x1
        val x2 = size.width * _line2Tail.transform(t)
        val width2 = size.width * _line2Head.transform(t) - x2
        _drawBar(color, x1, width1)
        _drawBar(color, x2, width2)
    }
}

private fun DrawScope._drawBar(color: Color, x: Float, width: Float) {
    if (width <= 0f) return
    drawRect(color, topLeft = Offset(x, 0f), size = Size(width, size.height))
}
