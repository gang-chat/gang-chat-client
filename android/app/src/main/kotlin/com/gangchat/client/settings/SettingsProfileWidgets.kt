package com.gangchat.client.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.AccountDeletionConfirmationSpec
import com.gangchat.client.app.matchesConfirmationText
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.ResponsiveDialogActionBar
import com.gangchat.client.ui.TextFieldContextMenu
import com.gangchat.client.ui.TextFieldEditingShortcuts
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTooltip
import com.gangchat.client.ui._EditableTextField
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.ui.uiTheme
import com.gangchat.client.widgets.Dialog
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.UndoHistoryController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.listen

/** Test tag of the delete-account confirmation field. */
const val settingsDeleteAccountInputTag = "settings-delete-account-input"

@Composable
internal fun _DeleteAccountDialog(spec: AccountDeletionConfirmationSpec) =
    StatefulWidget(_DeleteAccountDialogWidget(spec)) { _DeleteAccountDialogState() }

internal class _DeleteAccountDialogWidget(val spec: AccountDeletionConfirmationSpec)

private class _DeleteAccountDialogState : ViewState<_DeleteAccountDialogWidget>() {
    private val _controller = TextEditingController()
    private val _focusNode = FocusNode()
    private val _undoController = UndoHistoryController()

    override fun dispose() {
        _controller.dispose()
        _focusNode.dispose()
        _undoController.dispose()
    }

    @Composable
    override fun build() {
        val spec = widget.spec
        val matches = matchesConfirmationText(_controller.text, spec.expectedText)
        val shape = RoundedCornerShape(UiRadii.lg)
        Dialog {
            _StretchColumn(
                listOf(
                    {
                        Text(spec.title, style = uiTextStyle(color = _danger, fontSize = 18.0, fontWeight = FontWeight.W600))
                    },
                    _gap(10.dp),
                    { Text(spec.body, style = uiTextStyle(color = _textSecondary, fontSize = 13.0, height = 1.5)) },
                    _gap(14.dp),
                    { _DenseUnderlineTextField(hintText = spec.inputHint) },
                    _gap(18.dp),
                    {
                        ResponsiveDialogActionBar(
                            actions =
                                listOf(
                                    ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop(false) }),
                                    ResponsiveDialogAction(
                                        label = spec.confirmLabel,
                                        icon = Icons.Filled.DeleteOutline,
                                        tone = ButtonTone.danger,
                                        onPressed = if (matches) ({ Navigator.of(context).pop(true) }) else null,
                                    ),
                                ),
                        )
                    },
                ),
                Modifier
                    .widthIn(max = 420.dp)
                    .decoration(_primaryDarkLow, shape, UiColors.dangerBorder)
                    .padding(start = 22.dp, top = 20.dp, end = 22.dp, bottom = 18.dp),
            )
        }
    }

    /**
     * Dart's plain Material `TextField(decoration: InputDecoration(isDense:
     * true, hintText:))`: Material 3 body-large text, an 8 dp dense content
     * padding and an underline (1 dp `onSurfaceVariant`, 2 dp primary while
     * focused). The app's dark colour scheme leaves `onSurfaceVariant` at
     * `onSurface`.
     */
    @Composable
    private fun _DenseUnderlineTextField(hintText: String) {
        listen(_focusNode)
        LaunchedEffect(Unit) { _focusNode.requestFocus() }
        val style = uiTheme().textTheme.bodyLarge.merge(uiTextStyle(color = _textPrimary, fontWeight = FontWeight.W500))
        val focused = _focusNode.hasFocus
        val underline = if (focused) UiColors.accentStrong else UiColors.text
        val underlineWidth = if (focused) 2.dp else 1.dp
        TextFieldEditingShortcuts(
            controller = _controller,
            focusNode = _focusNode,
            undoController = _undoController,
            modifier = Modifier.fillMaxWidth(),
        ) {
            _EditableTextField(
                controller = _controller,
                focusNode = _focusNode,
                undoController = _undoController,
                style = style,
                onChanged = { setState() },
                contextMenu = TextFieldContextMenu(undoController = _undoController),
                modifier = Modifier.fillMaxWidth().testTag(settingsDeleteAccountInputTag),
            ) { innerTextField ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val stroke = underlineWidth.toPx()
                            drawRect(underline, topLeft = Offset(0f, size.height - stroke), size = androidx.compose.ui.geometry.Size(size.width, stroke))
                        }
                        .padding(top = 8.dp, bottom = 8.dp),
                ) {
                    if (_controller.text.isEmpty()) {
                        Text(hintText, style = style.merge(uiTextStyle(color = UiColors.text)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    innerTextField()
                }
            }
        }
    }
}

@Composable
internal fun _PasswordVisibilityToggle(obscure: Boolean, enabled: Boolean, onPressed: () -> Unit) {
    val color = if (enabled) _textSecondary else _textMuted
    val label = if (obscure) "显示密码" else "隐藏密码"
    UiTooltip(message = label) {
        Box(
            Modifier
                .semantics { contentDescription = label }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onPressed,
                )
                .size(width = 32.dp, height = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (obscure) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, size = 17.dp, color = color)
        }
    }
}

@Composable
internal fun _UsernameValidityIndicator(error: String?, checking: Boolean, enabled: Boolean) {
    val valid = error == null && !checking
    val color =
        when {
            !enabled -> _textMuted
            checking -> _textSecondary
            valid -> _cyan
            else -> _danger
        }
    val label = if (checking) "检测中" else if (valid) "合法" else "不合法"
    val message = if (checking) "正在检测登录用户名是否可用" else if (valid) "登录用户名可用" else error!!
    val semanticsLabel = if (checking) "正在检测登录用户名是否可用" else if (valid) "登录用户名可用" else "登录用户名不合法"
    UiTooltip(message = message) {
        Row(Modifier.semantics { contentDescription = semanticsLabel }, verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    checking -> Icons.Outlined.HourglassEmpty
                    valid -> Icons.Filled.CheckCircleOutline
                    else -> Icons.Filled.ErrorOutline
                },
                size = 16.dp,
                color = color,
            )
            Spacer(Modifier.width(5.dp))
            Text(label, style = uiTextStyle(color = color, fontSize = 12.0, fontWeight = FontWeight.W600))
        }
    }
}

/** Test tag of the verified-email mark (Dart's `ValueKey('settings-email-verified')`). */
const val settingsEmailVerifiedTag = "settings-email-verified"

/** Test tag of the email availability spinner icon (Dart's `ValueKey('settings-email-checking')`). */
const val settingsEmailCheckingTag = "settings-email-checking"

@Composable
internal fun _EmailVerificationStatusIndicator(checking: Boolean) {
    val message = if (checking) "正在检测邮箱是否可用" else "邮箱已验证"
    UiTooltip(message = message) {
        Box(Modifier.padding(horizontal = 8.dp)) {
            Icon(
                if (checking) Icons.Outlined.HourglassEmpty else Icons.Filled.CheckCircleOutline,
                modifier = Modifier.testTag(if (checking) settingsEmailCheckingTag else settingsEmailVerifiedTag),
                size = 18.dp,
                color = if (checking) _textSecondary else UiColors.accent,
            )
        }
    }
}
