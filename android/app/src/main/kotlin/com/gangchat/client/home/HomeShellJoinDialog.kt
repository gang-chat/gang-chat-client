package com.gangchat.client.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.ResponsiveDialogActionBar
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.widgets.Dialog
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration

// Port of lib/src/home/home_shell_join_dialog.dart.

/** Test tag of the join application reason field. */
const val joinApplicationReasonInputTag = "join-application-reason-input"

@Composable
internal fun _JoinApplicationDialog(room: PublicRoom) = StatefulWidget(_JoinApplicationDialogWidget(room)) { _JoinApplicationDialogState() }

internal class _JoinApplicationDialogWidget(val room: PublicRoom)

private class _JoinApplicationDialogState : ViewState<_JoinApplicationDialogWidget>() {
    private val _reasonController = TextEditingController()

    override fun dispose() {
        _reasonController.dispose()
    }

    private fun _close(result: String? = null) {
        Navigator.of(context, rootNavigator = true).pop(result)
    }

    private fun _cancel() {
        _close()
    }

    private fun _send() {
        _close(_reasonController.text)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun build() {
        val room = widget.room
        val shape = RoundedCornerShape(UiRadii.lg)
        Dialog(insetPadding = EdgeInsets.symmetric(horizontal = 20.dp, vertical = 20.dp)) {
            Column(
                Modifier
                    .widthIn(max = 430.dp)
                    // Like the dialog's Material, the panel takes pointer hits so a
                    // tap on its text or padding never reaches the barrier.
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                    .decoration(UiColors.surfaceLow, shape, UiColors.border)
                    .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.HowToReg, color = UiColors.accent, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "申请加入",
                        modifier = Modifier.weight(1f),
                        style = UiTypography.title.copy(fontWeight = FontWeight.W600),
                    )
                    ButtonIcon(
                        tooltip = "关闭",
                        icon = { Icon(Icons.Filled.Close) },
                        onPressed = { _cancel() },
                        size = 34.dp,
                    )
                }
                Spacer(Modifier.height(18.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("您需要等待", style = UiTypography.body.copy(color = UiColors.textSecondary))
                    Avatar(
                        label = room.name,
                        imageUrl = AppConfigScope.of(context).resolveAssetUrl(room.avatarUrl),
                        defaultAvatarKey = room.defaultAvatarKey,
                        size = 28.dp,
                    )
                    Text(
                        room.name,
                        modifier = Modifier.widthIn(max = 230.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = UiTypography.body.copy(fontWeight = FontWeight.W600),
                    )
                    Text("的管理员批准", style = UiTypography.body.copy(color = UiColors.textSecondary))
                }
                Spacer(Modifier.height(14.dp))
                Input(
                    modifier = Modifier.fillMaxWidth().testTag(joinApplicationReasonInputTag),
                    controller = _reasonController,
                    hintText = "申请说明",
                    minLines = 3,
                    maxLines = 4,
                )
                Spacer(Modifier.height(16.dp))
                ResponsiveDialogActionBar(
                    actions =
                        listOf(
                            ResponsiveDialogAction(label = "取消", onPressed = { _cancel() }),
                            ResponsiveDialogAction(label = "发送申请", tone = ButtonTone.primary, onPressed = { _send() }),
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
