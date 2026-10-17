package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon

private val _checkboxSize = 18.dp

@Composable
fun UiCheckbox(
    value: Boolean,
    onChanged: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tooltip: String? = null,
    semanticLabel: String? = null,
) {
    val interactive = enabled && onChanged != null
    val toggle = {
        if (interactive) onChanged?.invoke(!value)
    }
    val active = value
    val background =
        when {
            !active -> UiColors.fieldFill
            interactive -> UiColors.accentStrong
            else -> UiColors.accentStrong.copy(alpha = 0.35f)
        }
    val border =
        when {
            active -> Color.Transparent
            interactive -> UiColors.borderStrong
            else -> UiColors.disabledBorder
        }
    val label = semanticLabel ?: tooltip

    PressableSurface(
        width = _checkboxSize,
        height = _checkboxSize,
        enabled = interactive,
        interactive = interactive,
        onPressed = toggle,
        tooltip = tooltip,
        borderRadius = UiRadii.xs + 1.dp,
        padding = EdgeInsets.zero,
        backgroundColor = background,
        selectedBackgroundColor = background,
        disabledBackgroundColor = background,
        borderColor = border,
        selectedBorderColor = border,
        disabledBorderColor = border,
        modifier =
            modifier.semantics {
                toggleableState = ToggleableState(value)
                if (label != null) contentDescription = label
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Check,
                size = 14.dp,
                color =
                    when {
                        !active -> Color.Transparent
                        interactive -> UiColors.onAccent
                        else -> UiColors.textSecondary
                    },
            )
        }
    }
}
