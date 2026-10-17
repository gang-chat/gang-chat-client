package com.gangchat.client.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.Dialog
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.LocalMediaQuery
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.OverlayEntry
import com.gangchat.client.widgets.OverlayState
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.rememberBuildContext
import com.gangchat.client.widgets.showDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val floatingNoticeVisibleDuration: Duration = 3.seconds
private const val _floatingNoticeAnimationMillis = 180
private val _floatingNoticeTopInset = 56.dp

enum class FloatingNoticeTone { info, success, error }

/** A notice to show; a null [duration] keeps it until closed. [eventKey] distinguishes repeats. */
class FloatingNotice(
    val message: String,
    val tone: FloatingNoticeTone = FloatingNoticeTone.info,
    val duration: Duration? = floatingNoticeVisibleDuration,
    val eventKey: Any? = null,
) {
    val normalized: FloatingNotice?
        get() {
            val trimmed = message.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed == message) return this
            return FloatingNotice(message = trimmed, tone = tone, duration = duration, eventKey = eventKey)
        }

    override fun equals(other: Any?): Boolean =
        other is FloatingNotice &&
            other.message == message &&
            other.tone == tone &&
            other.duration == duration &&
            other.eventKey == eventKey

    override fun hashCode(): Int = java.util.Objects.hash(message, tone, duration, eventKey)
}

class AppNotificationController internal constructor() {
    internal var _host: _AppNotificationHostState? = null

    val isAttached: Boolean get() = _host != null

    fun show(
        message: String,
        tone: FloatingNoticeTone = FloatingNoticeTone.info,
        duration: Duration? = floatingNoticeVisibleDuration,
    ) {
        val notice = FloatingNotice(message = message, tone = tone, duration = duration).normalized ?: return
        _host?._show(notice)
    }
}

private val _LocalAppNotificationController = staticCompositionLocalOf<AppNotificationController?> { null }

/**
 * Hosts the app's floating notices above [child] (the app root installs it
 * around the navigator, like Dart's `MaterialApp(builder:)`).
 */
@Composable
fun AppNotificationHost(modifier: Modifier = Modifier, child: @Composable () -> Unit) =
    StatefulWidget(AppNotificationHostWidget(child, modifier)) { _AppNotificationHostState() }

/** Dart's static `AppNotificationHost.of` / `maybeOf`. */
object AppNotificationHost {
    fun maybeOf(context: BuildContext): AppNotificationController? = context[_LocalAppNotificationController]

    fun of(context: BuildContext): AppNotificationController =
        maybeOf(context) ?: throw IllegalStateException("No AppNotificationHost found in context.")
}

class AppNotificationHostWidget(val child: @Composable () -> Unit, val modifier: Modifier)

private class _FloatingNoticeEntry(val id: Int, val notice: FloatingNotice)

internal class _AppNotificationHostState : ViewState<AppNotificationHostWidget>() {
    private val _controller = AppNotificationController().also { it._host = this }
    private val _notices = mutableListOf<_FloatingNoticeEntry>()
    private var _nextNoticeId = 0

    fun _show(notice: FloatingNotice) {
        if (!mounted) return
        setState {
            _notices.add(0, _FloatingNoticeEntry(id = _nextNoticeId++, notice = notice))
            while (_notices.size > _maxVisibleNotices) _notices.removeAt(_notices.size - 1)
        }
    }

    private fun _remove(id: Int) {
        if (!mounted) return
        setState { _notices.removeAll { it.id == id } }
    }

    override fun dispose() {
        _controller._host = null
    }

    @Composable
    override fun build() {
        CompositionLocalProvider(_LocalAppNotificationController provides _controller) {
            Box(widget.modifier.fillMaxSize(), propagateMinConstraints = true) {
                widget.child()
                if (_notices.isNotEmpty()) {
                    _FloatingNoticeStack(entries = _notices.toList(), onDismissed = ::_remove)
                }
            }
        }
    }

    companion object {
        private const val _maxVisibleNotices = 3
    }
}

/**
 * Shows each notice in [notices] once, when it first appears (by value, so a
 * new [FloatingNotice.eventKey] shows the same message again).
 */
@Composable
fun FloatingNoticeEmitter(notices: List<FloatingNotice>, child: @Composable () -> Unit) =
    StatefulWidget(FloatingNoticeEmitterWidget(notices, child)) { _FloatingNoticeEmitterState() }

class FloatingNoticeEmitterWidget(val notices: List<FloatingNotice>, val child: @Composable () -> Unit)

private class _FloatingNoticeEmitterState : ViewState<FloatingNoticeEmitterWidget>() {
    private var _activeNotices: Set<FloatingNotice> = emptySet()
    private var _pending: List<FloatingNotice> = emptyList()

    override fun initState() = _syncNotices()

    override fun didUpdateWidget(oldWidget: FloatingNoticeEmitterWidget) = _syncNotices()

    private fun _syncNotices() {
        val next = LinkedHashSet<FloatingNotice>()
        for (notice in widget.notices) notice.normalized?.let { next.add(it) }
        val fresh = next - _activeNotices
        _activeNotices = next
        if (fresh.isEmpty()) return
        _pending = _pending + fresh
    }

    @Composable
    override fun build() {
        val pending = _pending
        if (pending.isNotEmpty()) {
            // Flutter shows them after the frame.
            LaunchedEffect(pending) {
                if (!mounted) return@LaunchedEffect
                _pending = emptyList()
                for (notice in pending) {
                    showFloatingNotice(context, notice.message, tone = notice.tone, duration = notice.duration)
                }
            }
        }
        widget.child()
    }
}

fun showFloatingNotice(
    context: BuildContext,
    message: String,
    tone: FloatingNoticeTone = FloatingNoticeTone.info,
    duration: Duration? = floatingNoticeVisibleDuration,
) {
    val notice = FloatingNotice(message = message, tone = tone, duration = duration).normalized ?: return
    val controller = AppNotificationHost.maybeOf(context)
    if (controller != null && controller.isAttached) {
        controller.show(notice.message, tone = notice.tone, duration = notice.duration)
        return
    }
    _showStandaloneFloatingNotice(context, notice)
}

fun showFloatingSuccessNotice(context: BuildContext, message: String, duration: Duration? = floatingNoticeVisibleDuration) =
    showFloatingNotice(context, message, tone = FloatingNoticeTone.success, duration = duration)

fun showFloatingErrorNotice(context: BuildContext, message: String, duration: Duration? = floatingNoticeVisibleDuration) =
    showFloatingNotice(context, message, tone = FloatingNoticeTone.error, duration = duration)

private fun _showStandaloneFloatingNotice(context: BuildContext, notice: FloatingNotice) {
    val overlay = OverlayState.maybeOf(context) ?: return
    lateinit var entry: OverlayEntry
    entry =
        OverlayEntry { _ ->
            _FloatingNoticeStack(
                entries = listOf(_FloatingNoticeEntry(id = 0, notice = notice)),
                onDismissed = { entry.remove() },
            )
        }
    overlay.insert(entry)
}

@Composable
private fun _FloatingNoticeStack(entries: List<_FloatingNoticeEntry>, onDismissed: (Int) -> Unit) {
    val top = LocalMediaQuery.current.padding.top + _floatingNoticeTopInset
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = top, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for ((index, entry) in entries.withIndex()) {
                key(entry.id) {
                    _AnimatedFloatingNotice(notice = entry.notice, onDismissed = { onDismissed(entry.id) })
                }
                if (index != entries.size - 1) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private class _AnimatedFloatingNoticeWidget(val notice: FloatingNotice, val onDismissed: () -> Unit)

@Composable
private fun _AnimatedFloatingNotice(notice: FloatingNotice, onDismissed: () -> Unit) =
    StatefulWidget(_AnimatedFloatingNoticeWidget(notice, onDismissed)) { _AnimatedFloatingNoticeState() }

private class _AnimatedFloatingNoticeState : ViewState<_AnimatedFloatingNoticeWidget>() {
    private val _animation = Animatable(0f)
    private var _reversing by mutableStateOf(false)
    private var _closing = false
    private var scope: CoroutineScope? = null
    private var _visibleTimer: kotlinx.coroutines.Job? = null

    private fun _close(animated: Boolean) {
        if (_closing) return
        _visibleTimer?.cancel()
        _visibleTimer = null
        _closing = true
        if (!animated) {
            widget.onDismissed()
            return
        }
        _reversing = true
        scope?.launch {
            _animation.animateTo(0f, tween(_floatingNoticeAnimationMillis, easing = LinearEasing))
            if (mounted) widget.onDismissed()
        }
    }

    @Composable
    override fun build() {
        val newScope = rememberCoroutineScope()
        scope = newScope
        LaunchedEffect(Unit) {
            _animation.animateTo(1f, tween(_floatingNoticeAnimationMillis, easing = LinearEasing))
            if (!mounted || _closing) return@LaunchedEffect
            val duration = widget.notice.duration
            if (duration != null) {
                _visibleTimer =
                    newScope.launch {
                        delay(duration)
                        _close(animated = true)
                    }
            }
        }
        val t = _animation.value
        val curved = if (_reversing) Curves.easeInCubic.transform(t) else Curves.easeOutCubic.transform(t)
        Box(
            Modifier.graphicsLayer {
                alpha = curved
                translationY = -0.16f * size.height * (1f - curved)
            },
        ) {
            Toast(
                message = widget.notice.message,
                tone = widget.notice.tone,
                icon = _noticeIcon(widget.notice.tone),
                onClose = { _close(animated = false) },
            )
        }
    }
}

private fun _noticeIcon(tone: FloatingNoticeTone): ImageVector =
    when (tone) {
        FloatingNoticeTone.success -> Icons.Filled.CheckCircleOutline
        FloatingNoticeTone.error -> Icons.Filled.ErrorOutline
        FloatingNoticeTone.info -> Icons.Outlined.Info
    }

private fun _noticeAccent(tone: FloatingNoticeTone): Color =
    when (tone) {
        FloatingNoticeTone.success -> UiColors.success
        FloatingNoticeTone.error -> UiColors.danger
        FloatingNoticeTone.info -> UiColors.accent
    }

/**
 * The toast's message style. Flutter renders notices outside any `Material`,
 * so nothing but these fields applies (no letter spacing, proportional
 * leading).
 */
internal val toastMessageStyle: TextStyle =
    TextStyle(
        color = UiColors.text,
        fontFamily = UiFonts.client,
        fontSize = 12.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.sp,
        lineHeight = 1.35.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
        textDecoration = TextDecoration.None,
    )

@Composable
fun Toast(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: FloatingNoticeTone = FloatingNoticeTone.info,
    onClose: (() -> Unit)? = null,
) {
    val accent = _noticeAccent(tone)
    val shape = RoundedCornerShape(UiRadii.md + 2.dp)
    Row(
        modifier
            .widthIn(max = 520.dp)
            // The toast body takes pointer hits (Flutter's DecoratedBox hit-tests its shape).
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .boxShadow(UiShadows.popover, shape)
            .decoration(UiColors.surfaceRaised, shape, UiColors.borderStrong)
            .padding(start = UiSpacing.sm, top = UiSpacing.sm, end = if (onClose == null) UiSpacing.md else UiSpacing.xs, bottom = UiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(24.dp).decoration(accent.copy(alpha = 0.16f), CircleShape), propagateMinConstraints = true) {
                Icon(icon, size = 15.dp, color = accent)
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(message, modifier = Modifier.weight(1f, fill = false), style = toastMessageStyle)
        if (onClose != null) {
            Spacer(Modifier.width(UiSpacing.sm))
            _NoticeCloseButton(onPressed = onClose)
        }
    }
}

/** Test tag of a notice's close button (Dart's `ValueKey('floating-notice-close-button')`). */
const val floatingNoticeCloseButtonTag = "floating-notice-close-button"

private class _NoticeCloseButtonWidget(val onPressed: () -> Unit)

@Composable
private fun _NoticeCloseButton(onPressed: () -> Unit) = StatefulWidget(_NoticeCloseButtonWidget(onPressed)) { _NoticeCloseButtonState() }

private class _NoticeCloseButtonState : ViewState<_NoticeCloseButtonWidget>() {
    private var _hovered = false
    private var _pressed = false

    private fun _setHovered(hovered: Boolean) {
        if (_hovered == hovered) return
        setState { _hovered = hovered }
    }

    private fun _setPressed(pressed: Boolean) {
        if (_pressed == pressed) return
        setState { _pressed = pressed }
    }

    @Composable
    override fun build() {
        val active = _hovered || _pressed
        val background by animateColorAsState(
            when {
                _pressed -> UiColors.surface
                active -> UiColors.surfaceHover
                else -> Color.Transparent
            },
            tween(95, easing = Curves.easeOutCubic),
            label = "noticeClose",
        )
        Box(
            Modifier
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "关闭"
                    onClick {
                        widget.onPressed()
                        true
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.type == PointerType.Mouse }) continue
                            when (event.type) {
                                PointerEventType.Enter -> _setHovered(true)
                                PointerEventType.Exit -> {
                                    _setHovered(false)
                                    _setPressed(false)
                                }
                                else -> Unit
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            _setPressed(true)
                            tryAwaitRelease()
                            _setPressed(false)
                        },
                        onTap = { widget.onPressed() },
                    )
                }
                .testTag(floatingNoticeCloseButtonTag)
                .size(26.dp)
                .decoration(background, RoundedCornerShape(UiRadii.sm)),
            propagateMinConstraints = true,
        ) {
            Icon(Icons.Filled.Close, size = 15.dp, color = if (active) UiColors.textSecondary else UiColors.textMuted)
        }
    }
}

/** A dialog action whose icon and layout adapt to the available width. [buttonKey] becomes the button's test tag. */
class ResponsiveDialogAction(
    val label: String,
    val onPressed: (() -> Unit)?,
    val buttonKey: String? = null,
    val icon: ImageVector? = null,
    val tone: ButtonTone = ButtonTone.neutral,
    val loading: Boolean = false,
)

private val _actionGap = 10.dp

/**
 * Lays out dialog actions in one row when every label fits in full: icons go
 * first when space is short, then the actions stack. [expanded] makes a
 * fitting row fill the width.
 */
@Composable
fun ResponsiveDialogActionBar(
    actions: List<ResponsiveDialogAction>,
    modifier: Modifier = Modifier,
    leadingActionCount: Int = 0,
    expanded: Boolean = false,
) {
    if (actions.isEmpty()) return
    val context = rememberBuildContext()
    BoxWithConstraints(modifier) {
        val finite = constraints.hasBoundedWidth
        val actionWidths = actions.map { Button.minimumWidthForLabel(context, label = it.label, hasIcon = it.icon != null) }
        val requiredWithIcons = actionWidths.fold(0.dp) { sum, width -> sum + width } + _actionGap * (actions.size - 1)
        val actionWidthsWithoutIcons = actions.map { Button.minimumWidthForLabel(context, label = it.label) }
        val requiredWithoutIcons = actionWidthsWithoutIcons.fold(0.dp) { sum, width -> sum + width } + _actionGap * (actions.size - 1)
        val showIcons = !finite || requiredWithIcons <= maxWidth
        val compact = !showIcons && finite
        val stack = finite && requiredWithoutIcons > maxWidth
        val splitIndex = leadingActionCount.coerceIn(0, actions.size)

        @Composable
        fun button(action: ResponsiveDialogAction, buttonModifier: Modifier = Modifier) {
            val icon = action.icon
            Button(
                onPressed = action.onPressed,
                tone = action.tone,
                loading = action.loading,
                icon = if (showIcons && icon != null) ({ Icon(icon) }) else null,
                width = if (compact || expanded || stack) Dp.Infinity else null,
                modifier = buttonModifier.then(action.buttonKey?.let { Modifier.testTag(it) } ?: Modifier),
            ) {
                Text(action.label)
            }
        }

        if (stack) {
            Column(Modifier.fillMaxWidth()) {
                for ((index, action) in actions.withIndex()) {
                    if (index > 0) Spacer(Modifier.height(_actionGap))
                    button(action)
                }
            }
            return@BoxWithConstraints
        }

        if (compact || expanded) {
            if (finite) {
                // Use each action's real content width on every platform. Equal
                // widths can truncate a long label even when the row has enough
                // total space. Give every action its measured minimum first, then
                // share the spare width so a fitting row still fills the dialog.
                val widths = if (showIcons) actionWidths else actionWidthsWithoutIcons
                val buttonSpace = maxWidth - _actionGap * (actions.size - 1)
                val naturalWidth = widths.fold(0.dp) { sum, width -> sum + width }
                val extraPerButton = ((buttonSpace - naturalWidth) / actions.size.toFloat()).coerceAtLeast(0.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for ((index, action) in actions.withIndex()) {
                        if (index > 0) Spacer(Modifier.width(_actionGap))
                        Box(Modifier.width(widths[index] + extraPerButton)) { button(action) }
                    }
                }
                return@BoxWithConstraints
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                for ((index, action) in actions.withIndex()) {
                    if (index > 0) Spacer(Modifier.width(_actionGap))
                    Box(Modifier.weight(1f)) { button(action) }
                }
            }
            return@BoxWithConstraints
        }

        val leading = actions.take(splitIndex)
        val trailing = actions.drop(splitIndex)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (splitIndex == 0) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for ((index, action) in leading.withIndex()) {
                if (index > 0) Spacer(Modifier.width(_actionGap))
                button(action)
            }
            if (leading.isNotEmpty() && trailing.isNotEmpty()) Spacer(Modifier.weight(1f))
            for ((index, action) in trailing.withIndex()) {
                if (index > 0) Spacer(Modifier.width(_actionGap))
                button(action)
            }
        }
    }
}

/**
 * The kit's dialog: a rounded panel with an optional icon tile, a title,
 * the content and the actions. Use inside `showDialog` (see [showUiDialog]).
 *
 * Give either [actions] (any composables, laid out like a Flutter `Wrap`
 * aligned to the end, where buttons fill the row), [adaptiveActions]
 * (preferred: a [ResponsiveDialogActionBar]) or a custom [actionBar].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogFrame(
    title: String,
    child: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actions: List<@Composable () -> Unit> = emptyList(),
    /**
     * Standard dialog actions that adapt to the available width.
     *
     * Each label is measured with the active text scale. Icons are removed first
     * when needed, then actions stack only if the complete labels still cannot
     * fit on one row.
     */
    adaptiveActions: List<ResponsiveDialogAction> = emptyList(),
    actionBar: (@Composable () -> Unit)? = null,
    maxWidth: Dp = 480.dp,
) {
    require(actions.isEmpty() || adaptiveActions.isEmpty())
    require(actions.isEmpty() || actionBar == null)
    require(adaptiveActions.isEmpty() || actionBar == null)
    val resolvedActionBar: (@Composable () -> Unit)? =
        if (adaptiveActions.isNotEmpty()) {
            { ResponsiveDialogActionBar(expanded = true, actions = adaptiveActions, modifier = Modifier.fillMaxWidth()) }
        } else {
            actionBar
        }
    val shape = RoundedCornerShape(UiRadii.xl)
    Dialog(insetPadding = EdgeInsets.symmetric(horizontal = 24.dp, vertical = 24.dp)) {
        Column(
            modifier
                .widthIn(max = maxWidth)
                // The panel takes pointer hits like Flutter's dialog `Material`:
                // a tap on its text or padding must not reach the dismissing
                // barrier below the route's page.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .boxShadow(UiShadows.dialog, shape)
                .decoration(UiColors.surfaceLow, shape, UiColors.borderStrong)
                .padding(start = 22.dp, top = 20.dp, end = 22.dp, bottom = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(Modifier.size(32.dp).decoration(UiColors.selected, RoundedCornerShape(UiRadii.md)), propagateMinConstraints = true) {
                        Icon(icon, size = 17.dp, color = UiColors.accent)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, modifier = Modifier.weight(1f), style = UiTypography.title)
            }
            Spacer(Modifier.height(16.dp))
            // Dialog applies the current viewInsets (including the Android IME)
            // before laying out this frame. Let the content yield that vertical
            // space instead of keeping a fixed child height and overflowing below
            // the action bar.
            Box(Modifier.weight(1f, fill = false).fillMaxWidth(), propagateMinConstraints = true) { child() }
            if (actions.isNotEmpty() || resolvedActionBar != null) {
                Spacer(Modifier.height(20.dp))
                if (resolvedActionBar != null) {
                    Box(Modifier.fillMaxWidth()) { resolvedActionBar() }
                } else {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // A Wrap gives its children a bounded width: Buttons fill it.
                        CompositionLocalProvider(LocalButtonFillsBoundedWidth provides true) {
                            for (action in actions) action()
                        }
                    }
                }
            }
        }
    }
}

/** Shows a [DialogFrame] with a single primary "完成" button and suspends until it closes. */
suspend fun showUiDialog(context: BuildContext, title: String, child: @Composable () -> Unit, icon: ImageVector? = null) {
    showDialog<Unit>(context) { dialogContext ->
        DialogFrame(
            title = title,
            icon = icon,
            actions =
                listOf {
                    Button(onPressed = { Navigator.of(dialogContext).pop() }, tone = ButtonTone.primary) { Text("完成") }
                },
            child = child,
        )
    }
}
