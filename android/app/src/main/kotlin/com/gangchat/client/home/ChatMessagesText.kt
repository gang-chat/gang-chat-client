package com.gangchat.client.home

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MessageMentionKind
import com.gangchat.client.app.MessageMentionRange
import com.gangchat.client.app.messageMentionKindForLabel
import com.gangchat.client.app.messageMentionRanges
import com.gangchat.client.app.resolveMessageMentionMember
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.Listenable
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.shell.ExternalUriLauncher
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.TextFieldContextMenu
import com.gangchat.client.ui.TextFieldEditingShortcuts
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui._EditableTextField
import com.gangchat.client.ui.removeAnyTextContextMenu
import com.gangchat.client.ui.uiTheme
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.LocalTapRegionRegistry
import com.gangchat.client.widgets.PointerDownEvent
import com.gangchat.client.widgets.PointerRouteEvent
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.TapRegion
import com.gangchat.client.widgets.TapRegionRegistry
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.UndoHistoryController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.kTouchSlop
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Part of the chat_messages.dart port: the selectable text body of a message
// bubble, with tappable links and @mentions.

/** Test tag of a text body's field (Dart's `ValueKey('message-text-layout-$id')` around the `TextField`). */
fun messageTextLayoutTag(messageId: String) = "message-text-layout-$messageId"

@Composable
internal fun _TextBody(
    message: Message,
    profileCurrentUser: CurrentUser,
    currentUser: UserSummary,
    ownerUserId: String?,
    mentionMembers: List<RoomMember>,
    mentionHighlighted: Boolean,
    onSelectionActiveChanged: (Boolean) -> Unit,
    selectionResetListenable: Listenable? = null,
    onResolveSenderProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterProfileRoom: ((PublicRoom) -> Unit)? = null,
    profileActionBuilder: UserProfileActionBuilder? = null,
    isUserInLive: ((String) -> Boolean)? = null,
) = StatefulWidget(
    _TextBodyWidget(
        message, profileCurrentUser, currentUser, ownerUserId, mentionMembers, mentionHighlighted, onSelectionActiveChanged,
        selectionResetListenable, onResolveSenderProfile, onResolveRoomProfile, onEnterProfileRoom, profileActionBuilder, isUserInLive,
    ),
) { _TextBodyState() }

private class _TextBodyWidget(
    val message: Message,
    val profileCurrentUser: CurrentUser,
    val currentUser: UserSummary,
    val ownerUserId: String?,
    val mentionMembers: List<RoomMember>,
    val mentionHighlighted: Boolean,
    val onSelectionActiveChanged: (Boolean) -> Unit,
    val selectionResetListenable: Listenable?,
    val onResolveSenderProfile: UserProfileResolver?,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onEnterProfileRoom: ((PublicRoom) -> Unit)?,
    val profileActionBuilder: UserProfileActionBuilder?,
    val isUserInLive: ((String) -> Boolean)?,
)

private class _TextBodyState : ViewState<_TextBodyWidget>() {
    // Dart's `_MessageTextController` is a TextEditingController subclass that
    // styles its text in `buildTextSpan`. Here the controller holds the plain
    // text and [_spans] styles it as the field's visual transformation.
    private lateinit var _controller: TextEditingController
    private lateinit var _spans: _MessageTextController
    private val _focusNode = FocusNode()
    private val _undoController = UndoHistoryController()
    private val _tapRegionGroup = Any()
    private var _lastPointerDownPosition: DpOffset? = null
    private var _textContextMenuOpen = false
    private var _textContextMenuActionPressed = false
    private var _androidOutsidePointer: Long? = null
    private var _androidOutsidePointerOrigin: DpOffset? = null
    private var _androidOutsideInitialSelection: TextRange? = null
    private var _androidOutsidePointerMoved = false
    private var _androidOutsideSelectionChanged = false
    private var _registry: TapRegionRegistry? = null
    private var _scope: CoroutineScope? = null
    private var _textLayout: TextLayoutResult? = null
    private var _coordinates: LayoutCoordinates? = null
    private var _density = 1f
    private val _handleControllerChangedListener: () -> Unit = { _handleControllerChanged() }
    private val _handleSelectionResetListener: () -> Unit = { _handleSelectionReset() }
    private val _androidOutsideRoute: (PointerRouteEvent) -> Unit = { event -> _handleAndroidOutsidePointerEvent(event) }

    override fun initState() {
        _controller = TextEditingController(text = _displayMessageBody(widget.message))
        _spans =
            _MessageTextController(
                currentUser = widget.currentUser,
                ownerUserId = widget.ownerUserId,
                mentions = widget.message.mentions,
                mentionMembers = widget.mentionMembers,
                mentionHighlighted = widget.mentionHighlighted,
                onOpenMentionUser = ::_handleOpenMentionUser,
                onOpenLink = ::_handleOpenLink,
            )
        _controller.addListener(_handleControllerChangedListener)
        widget.selectionResetListenable?.addListener(_handleSelectionResetListener)
    }

    override fun didUpdateWidget(oldWidget: _TextBodyWidget) {
        if (oldWidget.selectionResetListenable !== widget.selectionResetListenable) {
            oldWidget.selectionResetListenable?.removeListener(_handleSelectionResetListener)
            widget.selectionResetListenable?.addListener(_handleSelectionResetListener)
        }
        _spans.updateMentionContext(
            currentUser = widget.currentUser,
            ownerUserId = widget.ownerUserId,
            mentions = widget.message.mentions,
            mentionMembers = widget.mentionMembers,
            mentionHighlighted = widget.mentionHighlighted,
            onOpenMentionUser = ::_handleOpenMentionUser,
        )
        val oldBody = _displayMessageBody(oldWidget.message)
        val nextBody = _displayMessageBody(widget.message)
        if (oldBody != nextBody) {
            val selection = _controller.selection
            _controller.value = TextFieldValue(nextBody, _clampMessageTextSelection(selection, nextBody.length))
        }
    }

    override fun dispose() {
        _stopAndroidOutsidePointerTracking()
        widget.selectionResetListenable?.removeListener(_handleSelectionResetListener)
        _controller.removeListener(_handleControllerChangedListener)
        widget.onSelectionActiveChanged(false)
        _controller.dispose()
        _focusNode.dispose()
        _undoController.dispose()
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        _registry = LocalTapRegionRegistry.current
        val density = LocalDensity.current
        _density = density.density
        val measurer = rememberTextMeasurer()
        val style = uiTheme().textTheme.bodyLarge.merge(UiTypography.body)
        val transformation = VisualTransformation { original -> TransformedText(_spans.buildTextSpan(original.text, style), OffsetMapping.Identity) }

        TapRegion(groupId = _tapRegionGroup, onTapOutside = ::_handleTapOutside) {
            Box(
                Modifier
                    .onGloballyPositioned { _coordinates = it }
                    .pointerInput(this) { _observePointers(this) },
                propagateMinConstraints = true,
            ) {
                TextFieldEditingShortcuts(controller = _controller, focusNode = _focusNode, undoController = _undoController) {
                    // Dart's IntrinsicWidth + _TightReadOnlyEditableLayout: the
                    // field is exactly as wide as its styled text (wrapping only
                    // when the bubble is narrower), not as wide as the bubble or
                    // the editor's default minimum width.
                    Layout(
                        modifier = Modifier.testTag(messageTextLayoutTag(widget.message.id)),
                        content = {
                            _EditableTextField(
                                controller = _controller,
                                focusNode = _focusNode,
                                undoController = _undoController,
                                style = style,
                                readOnly = true,
                                showCursor = false,
                                minLines = 1,
                                maxLines = null,
                                visualTransformation = transformation,
                                onTextLayout = { _textLayout = it },
                                contextMenu =
                                    TextFieldContextMenu(
                                        readOnly = true,
                                        showReadOnlySelectAll = false,
                                        tapRegionGroupId = _tapRegionGroup,
                                        onOpenChanged = ::_handleTextContextMenuOpenChanged,
                                        onActionPressed = ::_handleTextContextMenuActionPressed,
                                    ),
                            ) { innerTextField -> innerTextField() }
                        },
                    ) { measurables, constraints ->
                        val styled = _spans.buildTextSpan(_controller.text, style)
                        val intrinsic =
                            ceil(
                                measurer.measure(styled, style, constraints = Constraints()).multiParagraph.maxIntrinsicWidth,
                            ).toInt()
                        val width = intrinsic.coerceIn(constraints.minWidth, constraints.maxWidth)
                        val placeable = measurables.first().measure(constraints.copy(minWidth = width, maxWidth = width))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                }
            }
        }
    }

    /**
     * Dart's span `TapGestureRecognizer`s plus the `Listener` that remembers
     * the last primary pointer down. A quick tap on a link or a resolved
     * mention is consumed before the read-only field sees it.
     */
    private suspend fun _observePointers(scope: androidx.compose.ui.input.pointer.PointerInputScope) {
        val slopPx = with(scope) { kTouchSlop.dp.toPx() }
        val longPressTimeout = scope.viewConfiguration.longPressTimeoutMillis
        scope.awaitPointerEventScope {
            var downId: PointerId? = null
            var downPosition = Offset.Zero
            var downTime = 0L
            var tapCandidate = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                when (event.type) {
                    PointerEventType.Press ->
                        for (change in event.changes) {
                            if (!change.pressed || change.previousPressed) continue
                            val primary = change.type != PointerType.Mouse || event.buttons.isPrimaryPressed
                            if (primary) _lastPointerDownPosition = _window(change.position)
                            if (downId != null) {
                                tapCandidate = false
                                continue
                            }
                            downId = change.id
                            downPosition = change.position
                            downTime = SystemClock.uptimeMillis()
                            tapCandidate = primary && _spanAt(change.position) != null
                        }
                    PointerEventType.Move ->
                        for (change in event.changes) {
                            if (change.id != downId) continue
                            val delta = change.position - downPosition
                            if (sqrt(delta.x * delta.x + delta.y * delta.y) > slopPx) tapCandidate = false
                        }
                    PointerEventType.Release ->
                        for (change in event.changes) {
                            if (change.id != downId || change.pressed) continue
                            downId = null
                            val elapsed = SystemClock.uptimeMillis() - downTime
                            if (!tapCandidate || change.isConsumed || elapsed >= longPressTimeout) continue
                            tapCandidate = false
                            val segment = _spanAt(change.position) ?: continue
                            change.consume()
                            segment.onTap()
                        }
                    else -> Unit
                }
            }
        }
    }

    private fun _window(local: Offset): DpOffset {
        val coords = _coordinates
        val position = if (coords != null && coords.isAttached) coords.localToWindow(local) else local
        return DpOffset((position.x / _density).dp, (position.y / _density).dp)
    }

    /**
     * Flutter's span hit test: the tappable span under a glyph that contains
     * the point (taps beside the text hit nothing).
     */
    private fun _spanAt(position: Offset): _MessageInlineSegment? {
        val layout = _textLayout ?: return null
        val text = _controller.text
        if (text.isEmpty()) return null
        val offset = layout.getOffsetForPosition(position)
        for (candidate in listOf(offset, offset - 1)) {
            if (candidate < 0 || candidate >= text.length) continue
            if (!layout.getBoundingBox(candidate).contains(position)) continue
            return _spans.tappableSegmentAt(text, candidate)
        }
        return null
    }

    private fun _handleControllerChanged() {
        val initialSelection = _androidOutsideInitialSelection
        if (initialSelection != null && _controller.selection != initialSelection) {
            _androidOutsideSelectionChanged = true
        }
        widget.onSelectionActiveChanged(_hasSelection)
    }

    private fun _handleSelectionReset() {
        _collapseSelection(hideToolbar = true)
        // The bubble context menu and the field both resolve the same Android
        // long press. The field can publish its selection later in this frame,
        // so reconcile once more after both have completed.
        _addPostFrameCallback {
            if (!mounted) return@_addPostFrameCallback
            _collapseSelection(hideToolbar = true)
        }
    }

    private fun _handleTextContextMenuActionPressed() {
        _textContextMenuActionPressed = true
    }

    private fun _handleTextContextMenuOpenChanged(open: Boolean) {
        if (!mounted) return
        if (open) {
            _textContextMenuOpen = true
            _textContextMenuActionPressed = false
            return
        }
        // Dart collapsed the selection after an outside click everywhere but
        // Android; here the selection stays.
        _textContextMenuOpen = false
        _textContextMenuActionPressed = false
    }

    private val _hasSelection: Boolean get() = !_controller.selection.collapsed

    private fun _handleTapOutside(event: PointerDownEvent) {
        // Android: wait for the pointer to finish (see below).
        _startAndroidOutsidePointerTracking(event)
    }

    private fun _startAndroidOutsidePointerTracking(event: PointerDownEvent) {
        if (!_hasSelection) return
        _stopAndroidOutsidePointerTracking()
        val registry = _registry ?: return
        _androidOutsidePointer = event.pointer
        _androidOutsidePointerOrigin = event.position
        _androidOutsideInitialSelection = _controller.selection
        _androidOutsidePointerMoved = false
        _androidOutsideSelectionChanged = false
        registry.addPointerRoute(event.pointer, _androidOutsideRoute)
    }

    private fun _handleAndroidOutsidePointerEvent(event: PointerRouteEvent) {
        if (event.pointer != _androidOutsidePointer) return
        val origin = _androidOutsidePointerOrigin
        if (origin != null && event.position != origin) {
            val dx = (event.position.x - origin.x).value
            val dy = (event.position.y - origin.y).value
            _androidOutsidePointerMoved = _androidOutsidePointerMoved || sqrt(dx * dx + dy * dy) > kTouchSlop
        }
        val initialSelection = _androidOutsideInitialSelection
        if (initialSelection != null && _controller.selection != initialSelection) {
            _androidOutsideSelectionChanged = true
        }
        if (!event.isUp) return

        // PORT: the pointer route reports an up and a cancel alike.
        val shouldCollapse = !_androidOutsidePointerMoved && !_androidOutsideSelectionChanged
        val selectionToCollapse = _androidOutsideInitialSelection
        _stopAndroidOutsidePointerTracking()
        if (!shouldCollapse || selectionToCollapse == null) return
        _addPostFrameCallback {
            if (!mounted || _controller.selection != selectionToCollapse) return@_addPostFrameCallback
            _collapseSelection(hideToolbar = true)
        }
    }

    private fun _stopAndroidOutsidePointerTracking() {
        val pointer = _androidOutsidePointer
        if (pointer != null) _registry?.removePointerRoute(pointer, _androidOutsideRoute)
        _androidOutsidePointer = null
        _androidOutsidePointerOrigin = null
        _androidOutsideInitialSelection = null
        _androidOutsidePointerMoved = false
        _androidOutsideSelectionChanged = false
    }

    private fun _collapseSelection(hideToolbar: Boolean = false) {
        val selection = _controller.selection
        if (selection.collapsed) return
        val offset = selection.end.coerceIn(0, _controller.text.length)
        _controller.selection = TextRange(offset)
        widget.onSelectionActiveChanged(false)
        if (hideToolbar) removeAnyTextContextMenu()
    }

    private fun _handleOpenLink(uri: Uri) {
        unawaited { _openLink(uri) }
    }

    private fun _handleOpenMentionUser(user: UserSummary) {
        val position = _lastPointerDownPosition ?: _fallbackProfilePosition()
        unawaited { _openMentionUserProfile(user, position) }
    }

    private suspend fun _openMentionUserProfile(user: UserSummary, position: DpOffset) {
        val resolver = widget.onResolveSenderProfile
        var displayUser = user
        if (resolver != null) {
            displayUser =
                try {
                    resolver(user)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    user
                }
        }
        if (!mounted) return
        showUserProfileCardAtPosition(
            context,
            position = position,
            user = displayUser,
            currentUser = widget.profileCurrentUser,
            onResolveProfile = resolver,
            onResolveRoomProfile = widget.onResolveRoomProfile,
            onEnterCommonRoom = widget.onEnterProfileRoom,
            profileActionBuilder = widget.profileActionBuilder,
            inLive = widget.isUserInLive?.invoke(displayUser.id) ?: false,
            showRoomRole = true,
            resolveOnOpen = false,
        )
    }

    private fun _fallbackProfilePosition(): DpOffset {
        val coords = _coordinates
        if (coords == null || !coords.isAttached) return DpOffset.Zero
        return _window(Offset(coords.size.width / 2f, coords.size.height.toFloat()))
    }

    private suspend fun _openLink(uri: Uri) {
        try {
            ExternalUriLauncher().open(uri)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            _showMessageContextNotice(context, "无法打开链接", tone = FloatingNoticeTone.error)
        }
    }

    private fun _addPostFrameCallback(callback: () -> Unit) {
        val scope = _scope ?: return
        scope.launch {
            withFrameNanos { }
            callback()
        }
    }
}

private fun _displayMessageBody(message: Message): String = message.body.trimEnd()

/**
 * Dart's `_MessageTextController` styling and gestures: links and @mentions
 * of the message text as styled, tappable segments.
 */
private class _MessageTextController(
    private var currentUser: UserSummary,
    private var ownerUserId: String?,
    private var mentions: List<JsonMap>,
    private var mentionMembers: List<RoomMember>,
    private var mentionHighlighted: Boolean,
    var onOpenMentionUser: (UserSummary) -> Unit,
    val onOpenLink: (Uri) -> Unit,
) {
    fun updateMentionContext(
        currentUser: UserSummary,
        ownerUserId: String?,
        mentions: List<JsonMap>,
        mentionMembers: List<RoomMember>,
        mentionHighlighted: Boolean,
        onOpenMentionUser: (UserSummary) -> Unit,
    ) {
        this.currentUser = currentUser
        this.ownerUserId = ownerUserId
        this.mentions = mentions
        this.mentionMembers = mentionMembers
        this.mentionHighlighted = mentionHighlighted
        this.onOpenMentionUser = onOpenMentionUser
    }

    private val _knownMentionLabels: List<String>
        get() =
            mentions.mapNotNull { mention ->
                val label = mention["label"] as String?
                if (label != null && label.trim().isNotEmpty()) label else null
            }

    /** The link and mention segments of [text], in order, without overlaps. */
    fun segments(text: String): List<_MessageInlineSegment> {
        val linkMatches = _messageLinkMatches(text)
        val mentionMatches =
            messageMentionRanges(text, labels = _knownMentionLabels).filter { range -> !_overlapsAnyLink(range, linkMatches) }
        if (linkMatches.isEmpty() && mentionMatches.isEmpty()) return emptyList()
        val segments =
            (linkMatches.map { _MessageInlineSegment.link(it, onOpenLink) } + mentionMatches.map { range -> _mentionSegment(text, range) })
                .sortedBy { it.start }
        val result = mutableListOf<_MessageInlineSegment>()
        var cursor = 0
        for (segment in segments) {
            if (segment.start < cursor) continue
            result.add(segment)
            cursor = segment.end
        }
        return result
    }

    private fun _mentionSegment(text: String, range: MessageMentionRange): _MessageInlineSegment {
        val label = text.substring(range.start + 1, range.end)
        val mentionMember =
            if (messageMentionKindForLabel(label) == MessageMentionKind.user) {
                resolveMessageMentionMember(label = label, members = mentionMembers)
            } else {
                null
            }
        val user = mentionMember?.user
        return _MessageInlineSegment.mention(range, if (user == null) null else ({ onOpenMentionUser(user) }))
    }

    fun tappableSegmentAt(text: String, offset: Int): _MessageInlineSegment? =
        segments(text).firstOrNull { segment -> offset >= segment.start && offset < segment.end && segment.tappable }

    /** Dart's `buildTextSpan`: links in the accent colour, underlined; mentions in bold control-accent. */
    fun buildTextSpan(text: String, style: TextStyle): AnnotatedString {
        val segments = segments(text)
        if (segments.isEmpty()) return AnnotatedString(text)
        val linkStyle = SpanStyle(color = UiColors.accent, textDecoration = TextDecoration.Underline)
        val mentionStyle = SpanStyle(color = UiColors.controlAccent, fontWeight = FontWeight.W600, textDecoration = TextDecoration.None)
        return buildAnnotatedString {
            append(text)
            for (segment in segments) {
                addStyle(if (segment.link != null) linkStyle else mentionStyle, segment.start, segment.end)
            }
        }
    }
}

private class _MessageInlineSegment private constructor(
    val link: _MessageLinkMatch?,
    val mention: MessageMentionRange?,
    private val tap: (() -> Unit)?,
) {
    val start: Int get() = link?.start ?: mention!!.start
    val end: Int get() = link?.end ?: mention!!.end
    val tappable: Boolean get() = tap != null

    fun onTap() {
        tap?.invoke()
    }

    companion object {
        fun link(link: _MessageLinkMatch, onOpenLink: (Uri) -> Unit) = _MessageInlineSegment(link, null) { onOpenLink(link.uri) }

        fun mention(mention: MessageMentionRange, onTap: (() -> Unit)?) = _MessageInlineSegment(null, mention, onTap)
    }
}

private fun _overlapsAnyLink(range: MessageMentionRange, links: List<_MessageLinkMatch>): Boolean {
    for (link in links) {
        if (range.start < link.end && range.end > link.start) return true
    }
    return false
}

private class _MessageLinkMatch(val start: Int, val end: Int, val uri: Uri)

/**
 * Dart's `RegExp(r"""(?:(?:https?:\/\/)|(?:www\.))[^\s<>{}\[\]"']+""",
 * caseSensitive: false)`. Dart regexps use JavaScript's `\s`, which also
 * matches Unicode spaces (U+3000, U+00A0, ...) that Java's default `\s` does
 * not.
 */
private val _messageLinkPattern =
    Regex(
        "(?:(?:https?://)|(?:www\\.))[^\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF<>{}\\[\\]\"']+",
        RegexOption.IGNORE_CASE,
    )

private const val _messageLinkTrailingPunctuation = ".,!?;:，。！？；：、)]}）】》"

private fun _messageLinkMatches(value: String): List<_MessageLinkMatch> {
    val matches = mutableListOf<_MessageLinkMatch>()
    for (match in _messageLinkPattern.findAll(value)) {
        val matchStart = match.range.first
        var end = match.range.last + 1
        while (end > matchStart && _messageLinkTrailingPunctuation.contains(value[end - 1])) {
            end--
        }
        if (end <= matchStart) continue
        val raw = value.substring(matchStart, end)
        val uri = _messageLinkUri(raw) ?: continue
        matches.add(_MessageLinkMatch(start = matchStart, end = end, uri = uri))
    }
    return matches
}

private fun _messageLinkUri(value: String): Uri? {
    val lower = value.lowercase()
    val normalized = if (lower.startsWith("http://") || lower.startsWith("https://")) value else "https://$value"
    val uri = Uri.tryParse(normalized) ?: return null
    if (!uri.hasScheme || uri.host.isEmpty()) return null
    if (uri.scheme != "http" && uri.scheme != "https") return null
    return uri
}

private fun _clampMessageTextSelection(selection: TextRange, length: Int): TextRange =
    TextRange(min(selection.start, length), min(selection.end, length))
