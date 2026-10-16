package com.gangchat.client.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration

/** Compact, flat category selector shared by search and dense filter rows. */
@Composable
fun CompactCategoryButton(
    label: String,
    selected: Boolean,
    onPressed: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    height: Dp = 30.dp,
) = StatefulWidget(CompactCategoryButtonWidget(label, count, selected, onPressed, height, modifier)) { _CompactCategoryButtonState() }

class CompactCategoryButtonWidget(
    val label: String,
    val count: Int?,
    val selected: Boolean,
    val onPressed: () -> Unit,
    val height: Dp,
    val modifier: Modifier,
)

private class _CompactCategoryButtonState : ViewState<CompactCategoryButtonWidget>() {
    private var _hovered = false

    @Composable
    override fun build() {
        val active = widget.selected || _hovered
        val count = widget.count
        val text = if (count == null) widget.label else "${widget.label} $count"
        val background by animateColorAsState(
            when {
                widget.selected -> UiColors.inverse
                active -> UiColors.surfaceHover
                else -> UiColors.surface
            },
            tween(90),
            label = "categoryBackground",
        )
        Box(
            widget.modifier
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.type == PointerType.Mouse }) continue
                            when (event.type) {
                                PointerEventType.Enter -> setState { _hovered = true }
                                PointerEventType.Exit -> setState { _hovered = false }
                                else -> Unit
                            }
                        }
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { widget.onPressed() },
                )
                .height(widget.height)
                .decoration(background, RoundedCornerShape(UiRadii.pill))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                    UiTypography.label.copyWith(
                        color = if (widget.selected) UiColors.onInverse else UiColors.textSecondary,
                        fontSize = 11.5,
                        fontWeight = FontWeight.W700,
                    ),
            )
        }
    }
}
