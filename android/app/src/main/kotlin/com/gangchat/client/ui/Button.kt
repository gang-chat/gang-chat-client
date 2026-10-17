package com.gangchat.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.DefaultTextStyle
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.IconTheme
import com.gangchat.client.widgets.IconThemeData
import com.gangchat.client.widgets.LocalDefaultTextStyle
import com.gangchat.client.widgets.LocalIconTheme
import com.gangchat.client.widgets.MainAxisSize
import com.gangchat.client.widgets.TextPainter

enum class ButtonTone { neutral, primary, danger }

/**
 * True where the Dart layout gave buttons a bounded width (a `Wrap`, or a
 * `Column` child), so a [Button] without an explicit width fills it like
 * Flutter's does. Compose layouts never pass the unbounded width a Flutter
 * `Row` does, so buttons shrink-wrap unless this or a width says otherwise.
 */
val LocalButtonFillsBoundedWidth = compositionLocalOf { false }

private val _labelStyle = uiTextStyle(fontSize = 13.0, fontWeight = FontWeight.W600, letterSpacing = 0.0)
private val _iconSize = 18.dp
private val _iconGap = 8.dp
private val _measurementSafety = 2.dp

/**
 * The kit's pill button. [child] is usually `Text(label)`; it gets the label
 * style, one line and an ellipsis. [icon] (usually `Icon(Icons.Rounded.X)`)
 * gets the tone's colour and an 18 dp size.
 *
 * Width: Flutter's Button fills a bounded parent and shrink-wraps under the
 * unbounded width of a `Row`. Compose has no unbounded constraints, so this
 * port shrink-wraps by default; pass [width] = `Dp.Infinity` where the Dart
 * button sat directly in a `Column`/`Wrap`/`SizedBox` and filled it.
 */
@Composable
fun Button(
    onPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    showIcon: Boolean = true,
    tooltip: String? = null,
    tone: ButtonTone = ButtonTone.neutral,
    selected: Boolean = false,
    toggleValue: Boolean? = null,
    onToggleChanged: ((Boolean) -> Unit)? = null,
    width: Dp? = null,
    height: Dp = 40.dp,
    padding: EdgeInsets = EdgeInsets.symmetric(horizontal = 16.dp),
    mainAxisSize: MainAxisSize = MainAxisSize.min,
    loading: Boolean = false,
    child: @Composable () -> Unit,
) {
    ButtonWidget(
        child = child,
        onPressed = onPressed,
        icon = icon,
        showIcon = showIcon,
        tooltip = tooltip,
        tone = tone,
        selected = selected,
        toggleValue = toggleValue,
        onToggleChanged = onToggleChanged,
        width = width,
        height = height,
        padding = padding,
        mainAxisSize = mainAxisSize,
        loading = loading,
        modifier = modifier,
    ).build()
}

/**
 * Dart's `Button` widget object, for code that keeps buttons as values and
 * rebuilds them ([withoutIcon]); call [build] to show it.
 */
class ButtonWidget(
    val child: @Composable () -> Unit,
    val onPressed: (() -> Unit)? = null,
    val icon: (@Composable () -> Unit)? = null,
    val showIcon: Boolean = true,
    val tooltip: String? = null,
    val tone: ButtonTone = ButtonTone.neutral,
    val selected: Boolean = false,
    val toggleValue: Boolean? = null,
    val onToggleChanged: ((Boolean) -> Unit)? = null,
    val width: Dp? = null,
    val height: Dp = 40.dp,
    val padding: EdgeInsets = EdgeInsets.symmetric(horizontal = 16.dp),
    val mainAxisSize: MainAxisSize = MainAxisSize.min,
    val loading: Boolean = false,
    val modifier: Modifier = Modifier,
) {
    /**
     * Returns the same button state and behavior without its leading icon.
     *
     * Responsive action groups use this only after their multi-row layout can
     * no longer show every label in full. Keeping the copy here ensures newly
     * added button state is handled alongside the component that owns it.
     */
    fun withoutIcon(): ButtonWidget {
        if (icon == null || !showIcon) return this
        return ButtonWidget(
            child = child,
            onPressed = onPressed,
            icon = icon,
            showIcon = false,
            tooltip = tooltip,
            tone = tone,
            selected = selected,
            toggleValue = toggleValue,
            onToggleChanged = onToggleChanged,
            width = width,
            height = height,
            padding = padding,
            mainAxisSize = mainAxisSize,
            loading = loading,
            modifier = modifier,
        )
    }

    @Composable
    fun build() {
        val toggleMode = toggleValue != null || onToggleChanged != null
        val toggled = toggleValue ?: false
        val visuallyEnabled = onPressed != null || onToggleChanged != null || loading
        val effectiveOnPressed: (() -> Unit)? =
            when {
                loading -> null
                toggleMode && onToggleChanged != null -> { { onToggleChanged.invoke(!toggled) } }
                else -> onPressed
            }
        val active = selected || toggled || tone == ButtonTone.primary
        val colors = _colorsFor(tone, visuallyEnabled, active = active)
        val resolvedIcon: (@Composable () -> Unit)? =
            when {
                loading && icon != null -> { { _ButtonLoadingIndicator(size = _iconSize) } }
                showIcon -> icon
                else -> null
            }

        @Composable
        fun surfaceFor(surfaceModifier: Modifier, surfaceWidth: Dp?) {
            PressableSurface(
                onPressed = effectiveOnPressed,
                tooltip = tooltip,
                enabled = visuallyEnabled,
                loading = loading,
                selected = active,
                width = surfaceWidth,
                height = height,
                padding = padding,
                borderRadius = UiRadii.pill,
                backgroundColor = colors.background,
                selectedBackgroundColor = colors.background,
                hoverBackgroundColor = colors.hoverBackground,
                pressedBackgroundColor = colors.pressedBackground,
                borderColor = colors.border,
                selectedBorderColor = colors.border,
                disabledBackgroundColor = colors.background,
                disabledBorderColor = colors.border,
                modifier = surfaceModifier,
            ) {
                _ButtonContent(
                    colors = colors,
                    icon = resolvedIcon,
                    mainAxisSize = mainAxisSize,
                    bounded = surfaceWidth != null,
                    child = child,
                )
            }
        }

        val requestedWidth = width ?: if (LocalButtonFillsBoundedWidth.current) Dp.Infinity else null
        if (requestedWidth == null) {
            surfaceFor(modifier, null)
        } else if (requestedWidth == Dp.Infinity || requestedWidth == Dp.Unspecified) {
            BoxWithConstraints(modifier) { surfaceFor(Modifier, maxWidth) }
        } else {
            surfaceFor(modifier, requestedWidth)
        }
    }

    companion object {
        /** Minimum width needed to show [label] without truncating it. */
        fun minimumWidthForLabel(
            context: BuildContext,
            label: String,
            hasIcon: Boolean = false,
            padding: EdgeInsets = EdgeInsets.symmetric(horizontal = 16.dp),
        ): Dp {
            val painter =
                TextPainter(label, context[LocalDefaultTextStyle].merge(_labelStyle), maxLines = 1).layout(context)
            return painter.width.dp + padding.horizontal + (if (hasIcon) _iconSize + _iconGap else 0.dp) + _measurementSafety
        }
    }
}

/** Dart's static `Button.minimumWidthForLabel`. */
object Button {
    fun minimumWidthForLabel(
        context: BuildContext,
        label: String,
        hasIcon: Boolean = false,
        padding: EdgeInsets = EdgeInsets.symmetric(horizontal = 16.dp),
    ): Dp = ButtonWidget.minimumWidthForLabel(context, label, hasIcon, padding)
}

@Composable
fun ButtonIcon(
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    tooltip: String? = null,
    onPressed: (() -> Unit)? = null,
    tone: ButtonTone = ButtonTone.neutral,
    selected: Boolean = false,
    toggleValue: Boolean? = null,
    onToggleChanged: ((Boolean) -> Unit)? = null,
    size: Dp = 40.dp,
    loading: Boolean = false,
    /**
     * Borderless toolbar style: transparent at rest, tinted on hover. Active
     * (selected/toggled) ghost buttons still show their tone colours.
     */
    ghost: Boolean = false,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    /** Width of a key-shaped button; defaults to [size] (a circle). */
    width: Dp? = null,
    radius: Dp = UiRadii.pill,
    iconSize: Dp? = null,
) {
    val toggleMode = toggleValue != null || onToggleChanged != null
    val toggled = toggleValue ?: false
    val visuallyEnabled = onPressed != null || onToggleChanged != null || loading
    val effectiveOnPressed: (() -> Unit)? =
        when {
            loading -> null
            toggleMode && onToggleChanged != null -> { { onToggleChanged.invoke(!toggled) } }
            else -> onPressed
        }
    val active = selected || toggled || tone == ButtonTone.primary
    val colors = _colorsFor(tone, visuallyEnabled, active = active, ghost = ghost, icon = true)
    val background = backgroundColor ?: colors.background
    val border = borderColor ?: colors.border
    val resolvedIconSize = iconSize ?: size * 0.46f

    PressableSurface(
        onPressed = effectiveOnPressed,
        tooltip = tooltip,
        enabled = visuallyEnabled,
        loading = loading,
        selected = active,
        width = width ?: size,
        height = size,
        padding = EdgeInsets.zero,
        borderRadius = radius,
        backgroundColor = background,
        selectedBackgroundColor = background,
        hoverBackgroundColor = if (backgroundColor == null) colors.hoverBackground else null,
        pressedBackgroundColor = colors.pressedBackground,
        borderColor = border,
        selectedBorderColor = border,
        disabledBackgroundColor = background,
        disabledBorderColor = border,
        modifier = modifier,
    ) {
        IconTheme(IconThemeData(color = colors.foreground, size = resolvedIconSize)) {
            Box(contentAlignment = Alignment.Center) {
                if (loading) _ButtonLoadingIndicator(size = resolvedIconSize) else icon()
            }
        }
    }
}

/**
 * 无背景、无边框的图标按钮，适用于标题行内的返回或导航操作。
 *
 * [width] 和 [height] 定义完整点击热区；图标本身保持较小尺寸，避免为了
 * 扩大热区而破坏标题行的视觉对齐。
 */
@Composable
fun ButtonIconPlain(
    icon: @Composable () -> Unit,
    onPressed: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tooltip: String? = null,
    width: Dp = 30.dp,
    height: Dp = 36.dp,
    iconSize: Dp = 20.dp,
) {
    val enabled = onPressed != null
    val body: @Composable (Modifier) -> Unit = { outer ->
        Box(
            outer
                .semantics { if (tooltip != null) contentDescription = tooltip }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = { onPressed?.invoke() },
                )
                .size(width, height),
            contentAlignment = Alignment.Center,
        ) {
            IconTheme(IconThemeData(color = if (enabled) UiColors.textSecondary else UiColors.textMuted, size = iconSize)) { icon() }
        }
    }
    if (tooltip != null && tooltip.isNotEmpty()) {
        UiTooltip(message = tooltip, modifier = modifier) { body(Modifier) }
    } else {
        body(modifier)
    }
}

@Composable
private fun _ButtonLoadingIndicator(size: Dp) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = LocalIconTheme.current.color ?: Color.White,
        strokeWidth = 2.dp,
        trackColor = Color.Transparent,
        strokeCap = StrokeCap.Square,
    )
}

@Composable
private fun _ButtonContent(
    colors: _ButtonColors,
    child: @Composable () -> Unit,
    mainAxisSize: MainAxisSize,
    bounded: Boolean,
    icon: (@Composable () -> Unit)?,
) {
    val label: @Composable (Modifier) -> Unit = { labelModifier ->
        DefaultTextStyle(_labelStyle.copyWith(color = colors.foreground), maxLines = 1, overflow = TextOverflow.Ellipsis) {
            Box(labelModifier.clipToBounds()) { child() }
        }
    }
    IconTheme(IconThemeData(color = colors.foreground, size = _iconSize)) {
        // Flutter: a bounded width lays the row across it (MainAxisSize.max).
        val fills = bounded || mainAxisSize == MainAxisSize.max
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = if (fills) Modifier.fillMaxWidth() else Modifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    icon()
                    Spacer(Modifier.width(_iconGap))
                }
                if (bounded) label(Modifier.weight(1f, fill = false)) else label(Modifier)
            }
        }
    }
}

private class _ButtonColors(
    val background: Color,
    val hoverBackground: Color,
    val pressedBackground: Color,
    val border: Color,
    val foreground: Color,
)

private val _neutralButtonSurface = Color(0xFF1A1A1D)
private val _neutralButtonBorder = Color(0xFF2E2E33)
private val _neutralIconForeground = Color(0xFFC9C9CD)

private fun _colorsFor(
    tone: ButtonTone,
    enabled: Boolean,
    active: Boolean = false,
    ghost: Boolean = false,
    icon: Boolean = false,
): _ButtonColors {
    if (!enabled) {
        return _ButtonColors(
            background = if (ghost) Color.Transparent else UiColors.disabledSurface,
            hoverBackground = if (ghost) Color.Transparent else UiColors.disabledSurface,
            pressedBackground = if (ghost) Color.Transparent else UiColors.disabledSurface,
            border = if (ghost) Color.Transparent else UiColors.disabledBorder,
            foreground = UiColors.textMuted,
        )
    }

    return when (tone) {
        ButtonTone.neutral -> {
            if (active) {
                // Selected / toggled-on controls invert to a white pill.
                _ButtonColors(
                    background = UiColors.inverse,
                    hoverBackground = Color(0xFFFFFFFF),
                    pressedBackground = Color(0xFFD9D9D6),
                    border = Color.Transparent,
                    foreground = UiColors.onInverse,
                )
            } else if (ghost) {
                _ButtonColors(
                    background = Color.Transparent,
                    hoverBackground = UiColors.surfaceHover,
                    pressedBackground = UiColors.surfaceRaised,
                    border = Color.Transparent,
                    foreground = _neutralIconForeground,
                )
            } else {
                _ButtonColors(
                    background = _neutralButtonSurface,
                    hoverBackground = UiStates.hover(_neutralButtonSurface),
                    pressedBackground = UiStates.pressed(_neutralButtonSurface),
                    border = _neutralButtonBorder,
                    foreground = if (icon) _neutralIconForeground else UiColors.text,
                )
            }
        }
        ButtonTone.primary ->
            _ButtonColors(
                background = UiColors.accentStrong,
                hoverBackground = UiColors.accent,
                pressedBackground = lerp(UiColors.accentStrong, Color.Black, 0.12f),
                border = Color.Transparent,
                foreground = UiColors.onAccent,
            )
        ButtonTone.danger ->
            _ButtonColors(
                background = if (ghost) Color.Transparent else UiColors.dangerSurface,
                hoverBackground = UiStates.hover(UiColors.dangerSurface),
                pressedBackground = UiStates.pressed(UiColors.dangerSurface),
                border = if (ghost) Color.Transparent else UiColors.dangerBorder,
                foreground = UiColors.danger,
            )
    }
}
