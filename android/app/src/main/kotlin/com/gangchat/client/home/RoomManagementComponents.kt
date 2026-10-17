package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.avatarUploadFilename
import com.gangchat.client.shell.FileSelectionService
import com.gangchat.client.shell.FileTypeGroup
import com.gangchat.client.shell.SelectedFile
import com.gangchat.client.ui.AvatarCropDialog
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.Segment
import com.gangchat.client.ui.SegmentedControl
import com.gangchat.client.ui.SettingsScaffold
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiSwitch
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Dialog
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.ignorePointer
import com.gangchat.client.widgets.rememberBuildContext
import com.gangchat.client.widgets.showDialog
import kotlinx.coroutines.CancellationException

/**
 * The frame of the room settings and members panels: embedded, a
 * [SettingsScaffold] page (the home shell's use); otherwise a fixed-size
 * dialog with its own title row and close button.
 */
@Composable
internal fun _RoomDialogShell(
    title: String,
    icon: ImageVector,
    onClose: () -> Unit,
    maxWidth: Dp,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    headerAction: (@Composable () -> Unit)? = null,
    pinned: (@Composable () -> Unit)? = null,
    embedded: Boolean = false,
    child: @Composable () -> Unit,
) {
    if (embedded) {
        SettingsScaffold(
            modifier = modifier,
            icon = icon,
            title = title,
            headerAction = headerAction,
            onBack = onClose,
            pinned = pinned,
            body = child,
        )
        return
    }

    Dialog(modifier = modifier, insetPadding = EdgeInsets.symmetric(horizontal = 20.dp, vertical = 20.dp)) {
        Column(
            Modifier
                .widthIn(max = maxWidth)
                .heightIn(max = maxHeight)
                .decoration(UiColors.surfaceLow, RoundedCornerShape(_panelRadius), UiColors.border)
                .padding(start = 22.dp, top = 16.dp, end = 22.dp, bottom = 18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, color = UiColors.accent, size = 19.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = uiTextStyle(color = UiColors.text, fontSize = 20.0, fontWeight = FontWeight.W600),
                )
                if (headerAction != null) {
                    headerAction()
                    Spacer(Modifier.width(4.dp))
                }
                ButtonIcon(tooltip = "关闭", icon = { Icon(Icons.Filled.Close) }, onPressed = onClose, size = 38.dp)
            }
            if (pinned != null) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth()) { pinned() }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), propagateMinConstraints = true) { child() }
        }
    }
}

/** A bordered row card; DecoratedBox, so the border does not inset the padding. */
@Composable
internal fun _RowSurface(modifier: Modifier = Modifier, compact: Boolean = false, child: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .decoration(UiColors.surfaceLow, RoundedCornerShape(_rowRadius), UiColors.border)
            .padding(horizontal = if (compact) 9.dp else 10.dp, vertical = if (compact) 7.dp else 9.dp),
        propagateMinConstraints = true,
    ) {
        child()
    }
}

@Composable
internal fun _RoomFieldLabel(label: String, modifier: Modifier = Modifier) {
    Text(label, modifier = modifier, style = UiTypography.label.copyWith(color = UiColors.textMuted))
}

@Composable
internal fun _EmptyState(icon: ImageVector, title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, color = UiColors.textMuted, size = 30.dp)
            Spacer(Modifier.height(8.dp))
            Text(title, style = UiTypography.body.copyWith(color = UiColors.textMuted))
        }
    }
}

/** [controlTag] is the segmented control's test tag (Dart's `controlKey`). */
@Composable
internal fun <T> _LabeledSegmented(
    label: String,
    value: T,
    segments: List<Segment<T>>,
    onChanged: (T) -> Unit,
    enabled: Boolean = true,
    controlTag: String? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        _RoomFieldLabel(label)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .ignorePointer(!enabled)
                .alpha(if (enabled) 1f else 0.5f),
        ) {
            SegmentedControl(
                modifier = if (controlTag != null) Modifier.testTag(controlTag) else Modifier,
                expanded = true,
                value = value,
                segments = segments,
                onChanged = onChanged,
            )
        }
    }
}

@Composable
internal fun _ToggleRow(
    label: String,
    value: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        _RoomFieldLabel(label, modifier = Modifier.weight(1f))
        UiSwitch(value = value, onChanged = if (enabled) onChanged else null, tooltip = label)
    }
}

@Composable
internal fun _ConfirmDialog(title: String, message: String, confirmLabel: String, danger: Boolean = false) {
    val context = rememberBuildContext()
    DialogFrame(
        title = title,
        icon = if (danger) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
        adaptiveActions =
            listOf(
                ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop(false) }),
                ResponsiveDialogAction(
                    label = confirmLabel,
                    tone = if (danger) ButtonTone.danger else ButtonTone.primary,
                    onPressed = { Navigator.of(context).pop(true) },
                ),
            ),
        child = { Text(message, style = UiTypography.body) },
    )
}

@Composable
internal fun _StrongConfirmDialog(title: String, message: String, expectedText: String, confirmLabel: String) =
    StatefulWidget(_StrongConfirmDialogWidget(title, message, expectedText, confirmLabel)) { _StrongConfirmDialogState() }

internal class _StrongConfirmDialogWidget(
    val title: String,
    val message: String,
    val expectedText: String,
    val confirmLabel: String,
)

private class _StrongConfirmDialogState : ViewState<_StrongConfirmDialogWidget>() {
    private val _controller = TextEditingController()
    private val _listener: () -> Unit = { setState() }

    override fun initState() {
        _controller.addListener(_listener)
    }

    override fun dispose() {
        _controller.removeListener(_listener)
        _controller.dispose()
    }

    @Composable
    override fun build() {
        val matches = _controller.text.trim() == widget.expectedText
        DialogFrame(
            title = widget.title,
            icon = Icons.Outlined.WarningAmber,
            adaptiveActions =
                listOf(
                    ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop(false) }),
                    ResponsiveDialogAction(
                        label = widget.confirmLabel,
                        tone = ButtonTone.danger,
                        onPressed = if (matches) ({ Navigator.of(context).pop(true) }) else null,
                    ),
                ),
            child = {
                Column(Modifier.fillMaxWidth()) {
                    Text(widget.message, style = UiTypography.body)
                    Spacer(Modifier.height(12.dp))
                    Input(controller = _controller, hintText = widget.expectedText)
                }
            },
        )
    }
}

internal class _CroppedRoomAvatar(val bytes: ByteArray, val filename: String)

/**
 * Open a file picker, read the bytes, and run them through the shared
 * [AvatarCropDialog]. Returns null if the user cancels at any step.
 */
internal suspend fun _pickAndCropRoomAvatar(context: BuildContext): _CroppedRoomAvatar? {
    val fileSelectionService = FileSelectionService()
    val file: SelectedFile?
    try {
        file =
            fileSelectionService.openFile(
                acceptedTypeGroups = listOf(FileTypeGroup(label = "图片", extensions = listOf("png", "jpg", "jpeg", "webp"))),
            )
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        throw Exception("无法打开文件选择器")
    }
    if (file == null) return null

    val bytes: ByteArray
    try {
        bytes = file.readAsBytes()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        throw Exception("无法读取图片")
    }
    if (bytes.isEmpty()) throw Exception("图片文件为空")
    if (!context.mounted) return null

    val cropped =
        showDialog<ByteArray>(context, barrierDismissible = false) { _ -> AvatarCropDialog(bytes = bytes, title = "裁剪图标") }
            ?: return null
    return _CroppedRoomAvatar(bytes = cropped, filename = avatarUploadFilename(file.name))
}
