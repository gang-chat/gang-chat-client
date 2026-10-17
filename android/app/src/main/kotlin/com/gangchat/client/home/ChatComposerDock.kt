package com.gangchat.client.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.ComposerAttachmentStatus
import com.gangchat.client.app.ComposerAttachmentView
import com.gangchat.client.app.MessageMentionKind
import com.gangchat.client.app.MessageMentionOption
import com.gangchat.client.app.StickerPanelBodyState
import com.gangchat.client.app.StickerPanelLoadState
import com.gangchat.client.app.StickerPanelSource
import com.gangchat.client.app.VoiceRecorderPhase
import com.gangchat.client.app.VoiceRecorderState
import com.gangchat.client.app.composerAttachmentGlyph
import com.gangchat.client.app.formatVoiceDuration
import com.gangchat.client.app.stickerPanelBodyState
import com.gangchat.client.app.stickerPanelEmptyText
import com.gangchat.client.app.stickerPanelStickers
import com.gangchat.client.app.userAvatarLabel
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageQuote
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.CachedAssetImage
import com.gangchat.client.ui.ChatComposer
import com.gangchat.client.ui.ChatComposerController
import com.gangchat.client.ui.ComposerAction
import com.gangchat.client.ui.ComposerActionAlignment
import com.gangchat.client.ui.ComposerPanel
import com.gangchat.client.ui.ComposerSuggestionAction
import com.gangchat.client.ui.ComposerSuggestionNavigation
import com.gangchat.client.ui.FloatingNotice
import com.gangchat.client.ui.FloatingNoticeEmitter
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.LocalAppConfig
import com.gangchat.client.ui.PressableSurface
import com.gangchat.client.ui.Segment
import com.gangchat.client.ui.SegmentedControl
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTooltip
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.roleBadgeForegroundColorForLabel
import com.gangchat.client.widgets.BoxFit
import com.gangchat.client.widgets.BoxShadow
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.OverlayPortal
import com.gangchat.client.widgets.OverlayPortalController
import com.gangchat.client.widgets.RenderBoxHandle
import com.gangchat.client.widgets.SafeArea
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.TextInputFormatter
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.rawScrollbar
import com.gangchat.client.widgets.rememberBuildContext
import com.gangchat.client.widgets.rememberScrollbarAdapter
import com.gangchat.client.widgets.renderBox
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Port of lib/src/home/chat_composer_dock.dart (a part of chat_pane.dart).

private val _stickerPanelHeight = 268.dp
private val _stickerTileSize = 56.dp
private val _voicePanelHeight = 150.dp
private val _mentionPanelWidth = 300.dp
private const val _mentionPanelVisibleRows = 4
private val _mentionPanelEmptyHeight = 52.dp
private val _mentionPanelVerticalPadding = 12.dp
private val _mentionPanelTileHeight = 44.dp
private val _mentionPanelTileGap = 2.dp
private val _mentionPanelHeight =
    _mentionPanelVerticalPadding + _mentionPanelTileHeight * _mentionPanelVisibleRows + _mentionPanelTileGap * (_mentionPanelVisibleRows - 1)
private val _mentionPanelGap = 8.dp

const val composerMessageComponentTag = "composer-message-component"
const val removeComposerMessageComponentTag = "remove-composer-message-component"
const val stickerPanelRefreshTag = "sticker-panel-refresh"
const val stickerPanelUploadFirstPersonalTag = "sticker-panel-upload-first-personal"
const val stickerPanelUploadFirstRoomTag = "sticker-panel-upload-first-room"
const val composerMentionPanelTag = "composer-mention-panel"

@Composable
internal fun _ComposerDock(
    controller: TextEditingController,
    composerController: ChatComposerController,
    timestampNow: DateTime,
    sending: Boolean,
    sendError: String?,
    stickerPanel: StickerPanelLoadState,
    voiceState: VoiceRecorderState,
    attachments: List<ComposerAttachmentView>,
    quotes: List<MessageQuote>,
    onRemoveQuote: ((String) -> Unit)?,
    component: Message?,
    onRemoveComponent: (() -> Unit)?,
    imagePreviewActions: ChatImagePreviewActions,
    fileActionHighlighted: Boolean,
    mentionOptions: List<MessageMentionOption>,
    mentionLoading: Boolean,
    mentionSelectedIndex: Int,
    onSelectMention: ((MessageMentionOption) -> Unit)?,
    inputFormatters: List<TextInputFormatter>?,
    onNavigateMentionSelection: ((ComposerSuggestionNavigation) -> Boolean)?,
    onConfirmMentionSelection: ((ComposerSuggestionAction) -> Boolean)?,
    onHighlightMentionSelection: ((Int) -> Unit)?,
    onSubmit: (String) -> Unit,
    onSendSticker: (Sticker) -> Unit,
    onOpenStickers: () -> Unit,
    onRefreshStickers: () -> Unit,
    onStickerSourceChanged: (StickerPanelSource) -> Unit,
    onUploadFirstPersonalSticker: (() -> Unit)?,
    onUploadFirstRoomSticker: (() -> Unit)?,
    onStartVoice: () -> Unit,
    onSendVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onPickFile: () -> Unit,
    onPasteFiles: suspend () -> Boolean,
    onCanPasteFiles: (suspend () -> Boolean)?,
    onRemoveAttachment: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
) {
    FloatingNoticeEmitter(
        notices =
            if (sendError != null) {
                listOf(FloatingNotice(message = sendError, tone = FloatingNoticeTone.error, duration = null))
            } else {
                emptyList()
            },
    ) {
        SafeArea(top = false) {
            Box(Modifier.fillMaxWidth().padding(EdgeInsets.fromLTRB(_composerHorizontalInset, 0.dp, _composerHorizontalInset, _composerBottomInset))) {
                _ComposerMentionOverlay(
                    visible = mentionOptions.isNotEmpty() || mentionLoading,
                    panel = {
                        _ComposerMentionPanel(
                            options = mentionOptions,
                            loading = mentionLoading,
                            selectedIndex = mentionSelectedIndex,
                            onHighlight = onHighlightMentionSelection,
                            onSelected = onSelectMention,
                        )
                    },
                ) {
                    ChatComposer(
                        controller = controller,
                        composerController = composerController,
                        hintText = "写点什么…",
                        maxLines = 5,
                        onSubmitted = onSubmit,
                        suggestionShortcutsEnabled = mentionOptions.isNotEmpty(),
                        onSuggestionNavigationPressed = onNavigateMentionSelection,
                        onSuggestionActionPressed = onConfirmMentionSelection,
                        inputFormatters = inputFormatters,
                        onPasteFiles = onPasteFiles,
                        onCanPasteFiles = onCanPasteFiles,
                        header =
                            if (quotes.isEmpty() && component == null) {
                                null
                            } else {
                                {
                                    Column(Modifier.fillMaxWidth()) {
                                        for ((index, quote) in quotes.withIndex()) {
                                            if (index > 0) Spacer(Modifier.height(6.dp))
                                            _MessageQuoteCard(
                                                quote = quote,
                                                timestampNow = timestampNow,
                                                onClose = if (onRemoveQuote == null) null else ({ onRemoveQuote(quote.messageId) }),
                                                compact = true,
                                                imagePreviewActions = imagePreviewActions,
                                            )
                                        }
                                        if (component != null) {
                                            if (quotes.isNotEmpty()) Spacer(Modifier.height(6.dp))
                                            _ComposerMessageComponentCard(message = component, onClose = onRemoveComponent)
                                        }
                                    }
                                }
                            },
                        attachments =
                            if (attachments.isEmpty()) {
                                null
                            } else {
                                { _ComposerAttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment, onRetry = onRetryAttachment) }
                            },
                        actions =
                            listOf(
                                ComposerAction(
                                    id = "stickers",
                                    icon = Icons.Outlined.EmojiEmotions,
                                    label = "表情",
                                    onPressed = onOpenStickers,
                                    panel =
                                        ComposerPanel.static(
                                            height = _stickerPanelHeight,
                                            padding = EdgeInsets.fromLTRB(12.dp, 12.dp, 12.dp, 12.dp),
                                            child = {
                                                _StickerPanel(
                                                    state = stickerPanel,
                                                    onSendSticker = onSendSticker,
                                                    onRefresh = onRefreshStickers,
                                                    onSourceChanged = onStickerSourceChanged,
                                                    onUploadFirstPersonalSticker = onUploadFirstPersonalSticker,
                                                    onUploadFirstRoomSticker = onUploadFirstRoomSticker,
                                                )
                                            },
                                        ),
                                ),
                                ComposerAction(
                                    id = "voice",
                                    icon = Icons.Filled.MicNone,
                                    label = "语音",
                                    onPressed = onCancelVoice,
                                    panel =
                                        ComposerPanel.static(
                                            height = _voicePanelHeight,
                                            child = { _VoicePanel(state = voiceState, onStart = onStartVoice, onSend = onSendVoice, onCancel = onCancelVoice) },
                                        ),
                                ),
                                ComposerAction(
                                    id = "file",
                                    icon = Icons.Filled.AttachFile,
                                    label = "文件",
                                    selected = fileActionHighlighted,
                                    onPressed = onPickFile,
                                ),
                                ComposerAction(
                                    id = "send",
                                    icon = Icons.AutoMirrored.Rounded.Send,
                                    label = "发送",
                                    tooltip = if (sending) "发送中" else "发送",
                                    tone = ButtonTone.primary,
                                    alignment = ComposerActionAlignment.trailing,
                                    onPressed = { onSubmit(controller.text) },
                                ),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun _ComposerMessageComponentCard(message: Message, onClose: (() -> Unit)?) {
    val playlist = message.playlistAttachment?.playlist
    val track = message.musicTrackAttachment?.track
    val isPlaylist = playlist != null
    val title = playlist?.name ?: track?.title ?: message.body.trim()
    val artists = track?.artists?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(" / ") ?: ""
    val subtitle =
        when {
            isPlaylist -> "${playlist!!.itemCount} 首歌曲"
            artists.isEmpty() -> "歌曲"
            else -> artists
        }
    // Container(padding: (10, 8, 6, 8)) plus its 1 px border.
    Row(
        Modifier
            .testTag(composerMessageComponentTag)
            .fillMaxWidth()
            .decoration(UiColors.surfaceRaised, RoundedCornerShape(8.dp), UiColors.border)
            .padding(EdgeInsets.fromLTRB(11.dp, 9.dp, 7.dp, 9.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (isPlaylist) Icons.AutoMirrored.Outlined.QueueMusic else Icons.Filled.MusicNote, color = UiColors.accent, size = 22.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = UiTypography.body.copyWith(color = UiColors.text, fontWeight = FontWeight.W700))
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = UiTypography.label.copyWith(color = UiColors.textMuted))
        }
        Spacer(Modifier.width(8.dp))
        _MaterialIconButton(
            icon = Icons.Filled.Close,
            modifier = Modifier.testTag(removeComposerMessageComponentTag),
            onPressed = onClose,
            iconSize = 16.dp,
            iconColor = UiColors.textMuted,
            tooltip = "移除组件",
            padding = EdgeInsets.zero,
            minWidth = 28.dp,
            minHeight = 28.dp,
        )
    }
}

@Composable
private fun _ComposerMentionOverlay(visible: Boolean, panel: @Composable () -> Unit, child: @Composable () -> Unit) =
    StatefulWidget(_ComposerMentionOverlayWidget(visible, panel, child)) { _ComposerMentionOverlayState() }

private class _ComposerMentionOverlayWidget(val visible: Boolean, val panel: @Composable () -> Unit, val child: @Composable () -> Unit)

private class _ComposerMentionOverlayState : ViewState<_ComposerMentionOverlayWidget>() {
    private val _anchorKey = RenderBoxHandle()
    private val _portal = OverlayPortalController()
    private var _scope: CoroutineScope? = null

    override fun didUpdateWidget(oldWidget: _ComposerMentionOverlayWidget) {
        _schedulePortalSync()
    }

    override fun dispose() {
        if (_portal.isShowing) _portal.hide()
    }

    private fun _schedulePortalSync() {
        val scope = _scope ?: return
        scope.launch {
            withFrameNanos { }
            if (!mounted) return@launch
            if (widget.visible) {
                if (!_portal.isShowing) _portal.show()
            } else if (_portal.isShowing) {
                _portal.hide()
            }
        }
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        _schedulePortalSync()
        OverlayPortal(
            controller = _portal,
            overlayChildBuilder = { _ ->
                // _ComposerMentionOverlayLayoutDelegate: above the composer,
                // 4 dp in from its left edge, kept inside the overlay.
                Layout(content = { widget.panel() }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
                    val placeable = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        if (!_anchorKey.hasSize) return@layout
                        val anchor = _anchorKey.globalRect
                        val origin = coordinates?.positionInWindow() ?: androidx.compose.ui.geometry.Offset.Zero
                        val anchorLeft = anchor.left.toPx() - origin.x
                        val anchorTop = anchor.top.toPx() - origin.y
                        val maxLeft = max(0f, (constraints.maxWidth - placeable.width).toFloat())
                        val left = (anchorLeft + 4.dp.toPx()).coerceIn(0f, maxLeft)
                        val maxTop = max(0f, (constraints.maxHeight - placeable.height).toFloat())
                        val top = (anchorTop - _mentionPanelGap.toPx() - placeable.height).coerceIn(0f, maxTop)
                        placeable.place(left.roundToInt(), top.roundToInt())
                    }
                }
            },
        ) {
            Box(Modifier.renderBox(_anchorKey)) { widget.child() }
        }
    }
}

@Composable
private fun _ComposerMentionPanel(
    options: List<MessageMentionOption>,
    loading: Boolean,
    selectedIndex: Int,
    onHighlight: ((Int) -> Unit)?,
    onSelected: ((MessageMentionOption) -> Unit)?,
) = StatefulWidget(_ComposerMentionPanelWidget(options, loading, selectedIndex, onHighlight, onSelected)) { _ComposerMentionPanelState() }

private class _ComposerMentionPanelWidget(
    val options: List<MessageMentionOption>,
    val loading: Boolean,
    val selectedIndex: Int,
    val onHighlight: ((Int) -> Unit)?,
    val onSelected: ((MessageMentionOption) -> Unit)?,
)

private class _ComposerMentionPanelState : ViewState<_ComposerMentionPanelWidget>() {
    private val _scrollController = ScrollState(0)
    private var _scope: CoroutineScope? = null
    private var _density = 1f
    private var _pendingSelectedVisible = false

    override fun initState() {
        _pendingSelectedVisible = true
    }

    override fun didUpdateWidget(oldWidget: _ComposerMentionPanelWidget) {
        if (oldWidget.selectedIndex != widget.selectedIndex || oldWidget.options.size != widget.options.size) {
            _scheduleSelectedVisible()
        }
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        _density = LocalDensity.current.density
        if (_pendingSelectedVisible) {
            _pendingSelectedVisible = false
            _scheduleSelectedVisible()
        }
        val config = LocalAppConfig.current
        val rows = widget.options
        val height = _mentionPanelHeightFor(rows.size)
        val selectedIndex = _effectiveSelectedIndex(rows.size)
        val shape = RoundedCornerShape(UiRadii.md)
        Box(
            Modifier
                .testTag(composerMentionPanelTag)
                .size(_mentionPanelWidth, height)
                .boxShadow(listOf(BoxShadow(color = Color(0x66000000), blurRadius = 18.dp, offset = DpOffset(0.dp, 10.dp))), shape)
                .decoration(UiColors.surfaceRaised, shape, UiColors.borderStrong)
                .clip(shape),
        ) {
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (widget.loading) {
                        _ChatProgressIndicator(size = 18.dp, strokeWidth = 2.dp, color = UiColors.accent)
                    } else {
                        Text("没有匹配的成员", style = UiTypography.label.copyWith(color = UiColors.textMuted))
                    }
                }
            } else {
                // The theme's Material scrollbar, always shown past four rows.
                Column(
                    Modifier
                        .fillMaxSize()
                        .rawScrollbar(
                            rememberScrollbarAdapter(_scrollController),
                            thickness = 6.dp,
                            radius = UiRadii.pill,
                            thumbColor = UiColors.borderStrong,
                            thumbVisibility = rows.size > 4,
                            crossAxisMargin = 2.dp,
                            mainAxisMargin = 4.dp,
                        )
                        .verticalScroll(_scrollController)
                        .padding(EdgeInsets.symmetric(vertical = 6.dp)),
                    verticalArrangement = Arrangement.spacedBy(_mentionPanelTileGap),
                ) {
                    for ((index, option) in rows.withIndex()) {
                        val user = option.member?.user
                        val nameColor = if (option.isUser) roleBadgeForegroundColorForLabel(option.roleLabel) else UiColors.accent
                        _ComposerMentionTile(
                            option = option,
                            avatarUrl = config.resolveAssetUrl(user?.avatarUrl),
                            nameColor = nameColor,
                            highlighted = selectedIndex == index,
                            onHover = { widget.onHighlight?.invoke(index) },
                            onSelected = widget.onSelected,
                        )
                    }
                }
            }
        }
    }

    private fun _effectiveSelectedIndex(rowCount: Int): Int {
        if (rowCount <= 0) return 0
        return widget.selectedIndex.coerceIn(0, rowCount - 1)
    }

    private fun _scheduleSelectedVisible() {
        val scope = _scope
        if (scope == null) {
            _pendingSelectedVisible = true
            return
        }
        scope.launch {
            withFrameNanos { }
            if (!mounted) return@launch
            val rowCount = widget.options.size
            if (rowCount <= 0) return@launch
            val index = _effectiveSelectedIndex(rowCount)
            val density = _density
            val rowTop = (6f + index * (_mentionPanelTileHeight.value + _mentionPanelTileGap.value)) * density
            val rowBottom = rowTop + _mentionPanelTileHeight.value * density
            val position = _scrollController
            val viewport = position.viewportSize.toFloat()
            var target = position.value.toFloat()
            if (rowTop < position.value) {
                target = rowTop
            } else if (rowBottom > position.value + viewport) {
                target = rowBottom - viewport
            }
            val targetPx = target.coerceIn(0f, position.maxValue.toFloat()).roundToInt()
            if (targetPx == position.value) return@launch
            position.animateScrollTo(targetPx, tween(90, easing = Curves.easeOutCubic))
        }
    }
}

private fun _mentionPanelHeightFor(rowCount: Int) =
    when {
        rowCount <= 0 -> _mentionPanelEmptyHeight
        rowCount >= _mentionPanelVisibleRows -> _mentionPanelHeight
        else -> _mentionPanelVerticalPadding + _mentionPanelTileHeight * rowCount + _mentionPanelTileGap * (rowCount - 1)
    }

@Composable
private fun _ComposerMentionTile(
    option: MessageMentionOption,
    avatarUrl: String?,
    nameColor: Color,
    highlighted: Boolean,
    onHover: () -> Unit,
    onSelected: ((MessageMentionOption) -> Unit)?,
) {
    val background by animateColorAsState(
        if (highlighted) UiColors.selected else Color.Transparent,
        tween(120, easing = Curves.easeOutCubic),
        label = "mentionTileBackground",
    )
    Row(
        Modifier
            .padding(EdgeInsets.symmetric(horizontal = 6.dp))
            .fillMaxWidth()
            .height(_mentionPanelTileHeight)
            // MouseRegion(onEnter:): a hovering mouse highlights the row.
            .pointerInput(onHover) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.type == PointerEventType.Enter && event.changes.any { it.type == PointerType.Mouse }) onHover()
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = onSelected != null,
                onClick = { onSelected?.invoke(option) },
            )
            .decoration(background, RoundedCornerShape(UiRadii.sm))
            .padding(EdgeInsets.symmetric(horizontal = 8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        _ComposerMentionTileLeading(option = option, avatarUrl = avatarUrl)
        Spacer(Modifier.width(10.dp))
        Text(
            option.label,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = UiTypography.body.copyWith(color = nameColor, fontWeight = FontWeight.W700),
        )
    }
}

@Composable
private fun _ComposerMentionTileLeading(option: MessageMentionOption, avatarUrl: String?) {
    val user = option.member?.user
    if (user != null) {
        Avatar(label = userAvatarLabel(user), imageUrl = avatarUrl, defaultAvatarKey = user.defaultAvatarKey, size = 30.dp, showBorder = false)
        return
    }
    val icon =
        when (option.kind) {
            MessageMentionKind.everyone -> Icons.Outlined.Campaign
            MessageMentionKind.admins -> Icons.Outlined.AdminPanelSettings
            MessageMentionKind.user -> Icons.Filled.AlternateEmail
            MessageMentionKind.bot -> Icons.Outlined.SmartToy
        }
    Box(Modifier.size(30.dp).decoration(UiColors.selected, CircleShape, UiColors.selectedBorder), contentAlignment = Alignment.Center) {
        Icon(icon, color = UiColors.accent, size = 17.dp)
    }
}

/**
 * The composer's sticker picker. Shows the user's personal packs and the
 * current room's packs behind a source toggle, sending the real managed
 * sticker (asset + id) rather than a decorative placeholder.
 */
@Composable
private fun _StickerPanel(
    state: StickerPanelLoadState,
    onSendSticker: (Sticker) -> Unit,
    onRefresh: () -> Unit,
    onSourceChanged: (StickerPanelSource) -> Unit,
    onUploadFirstPersonalSticker: (() -> Unit)?,
    onUploadFirstRoomSticker: (() -> Unit)?,
) {
    val stickers = stickerPanelStickers(source = state.source, personalPacks = state.personalPacks, roomPacks = state.roomPacks)
    val bodyState = stickerPanelBodyState(loading = state.loading, error = state.error, stickers = stickers)
    Column(Modifier.fillMaxSize()) {
        SegmentedControl(
            modifier = Modifier.fillMaxWidth(),
            value = state.source,
            expanded = true,
            onChanged = onSourceChanged,
            segments =
                listOf(
                    Segment(value = StickerPanelSource.personal, label = "我的表情包"),
                    Segment(value = StickerPanelSource.room, label = "房间表情包"),
                ),
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), propagateMinConstraints = true) {
            when (bodyState) {
                StickerPanelBodyState.loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        _ChatProgressIndicator(size = 22.dp, strokeWidth = 2.2.dp, color = UiColors.accent)
                    }
                StickerPanelBodyState.error ->
                    _StickerPanelMessage(
                        icon = Icons.Rounded.WarningAmber,
                        text = state.error ?: "加载表情失败",
                        actionKey = stickerPanelRefreshTag,
                        actionIcon = Icons.Filled.Refresh,
                        actionLabel = "刷新",
                        onAction = onRefresh,
                    )
                StickerPanelBodyState.empty ->
                    _StickerPanelMessage(
                        icon = Icons.Outlined.EmojiEmotions,
                        text = stickerPanelEmptyText(state.source),
                        actionKey =
                            if (state.source == StickerPanelSource.personal) stickerPanelUploadFirstPersonalTag else stickerPanelUploadFirstRoomTag,
                        actionIcon = Icons.Outlined.UploadFile,
                        actionLabel = "上传第一个表情",
                        actionTone = ButtonTone.primary,
                        onAction = if (state.source == StickerPanelSource.personal) onUploadFirstPersonalSticker else onUploadFirstRoomSticker,
                    )
                StickerPanelBodyState.results -> _StickerList(stickers = stickers, onSendSticker = onSendSticker)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun _StickerList(stickers: List<Sticker>, onSendSticker: (Sticker) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (sticker in stickers) {
                _StickerTile(sticker = sticker, onPressed = { onSendSticker(sticker) })
            }
        }
    }
}

@Composable
private fun _StickerTile(sticker: Sticker, onPressed: () -> Unit) {
    val imageUrl = LocalAppConfig.current.resolveAssetUrl(sticker.asset.thumbnailUrl ?: sticker.asset.url)
    UiTooltip(message = sticker.name) {
        PressableSurface(
            width = _stickerTileSize,
            height = _stickerTileSize,
            onPressed = onPressed,
            padding = EdgeInsets.all(6.dp),
            backgroundColor = UiColors.surfaceLow,
            borderColor = UiColors.border,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (imageUrl == null) {
                    Icon(Icons.Outlined.ImageNotSupported, color = UiColors.textMuted, size = 20.dp)
                } else {
                    CachedAssetImage(
                        url = imageUrl,
                        filename = sticker.asset.filename,
                        mimeType = sticker.asset.mimeType,
                        expectedBytes = sticker.asset.sizeBytes,
                        fit = BoxFit.contain,
                        errorBuilder = { _, _ -> Icon(Icons.Outlined.BrokenImage, color = UiColors.textMuted, size = 20.dp) },
                    )
                }
            }
        }
    }
}

@Composable
private fun _StickerPanelMessage(
    icon: ImageVector,
    text: String,
    actionKey: String? = null,
    actionIcon: ImageVector? = null,
    actionLabel: String? = null,
    actionTone: ButtonTone = ButtonTone.neutral,
    onAction: (() -> Unit)? = null,
) {
    val context = rememberBuildContext()
    val actionWidth =
        if (actionLabel == null) null else Button.minimumWidthForLabel(context, label = actionLabel, hasIcon = actionIcon != null)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, color = UiColors.textMuted, size = 26.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                style = UiTypography.label.copyWith(color = UiColors.textMuted),
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    modifier = if (actionKey != null) Modifier.testTag(actionKey) else Modifier,
                    icon = if (actionIcon == null) null else ({ Icon(actionIcon) }),
                    tone = actionTone,
                    width = actionWidth,
                    onPressed = onAction,
                ) { Text(actionLabel) }
            }
        }
    }
}

/**
 * The strip of staged-file chips shown above the composer input. Each chip
 * names a file queued for the next message and offers a remove button. The
 * files ride out as attachments when the message is sent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun _ComposerAttachmentStrip(attachments: List<ComposerAttachmentView>, onRemove: (String) -> Unit, onRetry: (String) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (attachment in attachments) {
                _ComposerAttachmentChip(attachment = attachment, onRemove = { onRemove(attachment.id) }, onRetry = { onRetry(attachment.id) })
            }
        }
    }
}

@Composable
private fun _ComposerAttachmentChip(attachment: ComposerAttachmentView, onRemove: () -> Unit, onRetry: () -> Unit) {
    val size = attachment.sizeLabel
    val failed = attachment.hasFailed
    val progress = attachment.progress
    val subtitle =
        when (attachment.status) {
            ComposerAttachmentStatus.failed -> attachment.errorMessage ?: "上传失败，点击重试"
            ComposerAttachmentStatus.uploading -> if (progress == null) "上传中…" else "上传中 ${(progress * 100).roundToInt()}%"
            ComposerAttachmentStatus.uploaded -> size
        }
    // DecoratedBox + Padding: the padding does not include the border.
    Row(
        Modifier
            .decoration(UiColors.surface, RoundedCornerShape(UiRadii.md), if (failed) UiColors.danger else UiColors.border)
            .padding(EdgeInsets.fromLTRB(10.dp, 6.dp, 4.dp, 6.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        _ComposerAttachmentLeading(attachment = attachment, onRetry = onRetry)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.widthIn(max = 180.dp)) {
            UiTooltip(message = attachment.filename) {
                Column {
                    Text(
                        attachment.filename,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = UiTypography.label.copyWith(color = UiColors.text),
                    )
                    if (subtitle != null) {
                        Text(subtitle, style = UiTypography.label.copyWith(color = if (failed) UiColors.danger else UiColors.textMuted, fontSize = 11.0))
                    }
                }
            }
        }
        _MaterialIconButton(
            icon = Icons.Filled.Close,
            onPressed = onRemove,
            iconSize = 16.dp,
            iconColor = UiColors.textMuted,
            tooltip = "移除",
            padding = EdgeInsets.zero,
            minWidth = 28.dp,
            minHeight = 28.dp,
        )
    }
}

/**
 * The chip's leading slot: a spinner while uploading, a tappable retry icon
 * when the upload failed, and the file-type glyph once it has landed.
 */
@Composable
private fun _ComposerAttachmentLeading(attachment: ComposerAttachmentView, onRetry: () -> Unit) {
    when (attachment.status) {
        ComposerAttachmentStatus.uploading ->
            _ChatProgressIndicator(size = 18.dp, strokeWidth = 2.dp, color = UiColors.accent, value = attachment.progress)
        ComposerAttachmentStatus.failed ->
            Box(
                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRetry),
            ) {
                Icon(Icons.Filled.Refresh, size = 18.dp, color = UiColors.danger)
            }
        ComposerAttachmentStatus.uploaded ->
            Icon(
                composerAttachmentGlyph(mimeType = attachment.mimeType, filename = attachment.filename),
                size = 18.dp,
                color = UiColors.textSecondary,
            )
    }
}

@Composable
private fun _VoicePanel(state: VoiceRecorderState, onStart: () -> Unit, onSend: () -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state.phase) {
            VoiceRecorderPhase.idle -> _VoiceIdle(error = state.error, onStart = onStart)
            VoiceRecorderPhase.recording ->
                _VoiceActive(elapsed = state.elapsed, recording = true, busy = false, onSend = onSend, onCancel = onCancel)
            VoiceRecorderPhase.review ->
                _VoiceActive(elapsed = state.elapsed, recording = false, busy = false, error = state.error, onSend = onSend, onCancel = onCancel)
            VoiceRecorderPhase.sending ->
                _VoiceActive(elapsed = state.elapsed, recording = false, busy = true, onSend = onSend, onCancel = onCancel)
        }
    }
}

@Composable
private fun _VoiceIdle(onStart: () -> Unit, error: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(14.dp))
        // Plain icon trigger — no raised surface or circular background.
        _MaterialIconButton(icon = Icons.Filled.MicNone, onPressed = onStart, iconSize = 30.dp, iconColor = UiColors.accent, tooltip = "点击录音")
        Spacer(Modifier.height(8.dp))
        Text(
            error ?: "点击录音",
            textAlign = TextAlign.Center,
            style = UiTypography.label.copyWith(color = if (error != null) UiColors.danger else UiColors.textMuted),
        )
    }
}

@Composable
private fun _VoiceActive(
    elapsed: kotlin.time.Duration,
    recording: Boolean,
    busy: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    error: String? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (recording) Icons.Filled.FiberManualRecord else Icons.Filled.MicNone,
                size = 16.dp,
                color = if (recording) UiColors.danger else UiColors.textSecondary,
            )
            Spacer(Modifier.width(8.dp))
            Text(formatVoiceDuration(elapsed), style = UiTypography.body.copy(fontFeatureSettings = "tnum"))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            error ?: if (recording) "正在录音…" else "点击发送或取消",
            textAlign = TextAlign.Center,
            style = UiTypography.label.copyWith(color = if (error != null) UiColors.danger else UiColors.textMuted),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ButtonIcon(
                onPressed = if (busy) null else onCancel,
                icon = { Icon(Icons.Filled.DeleteOutline) },
                tooltip = "取消",
                tone = ButtonTone.danger,
                size = 34.dp,
            )
            Spacer(Modifier.width(10.dp))
            ButtonIcon(
                onPressed = if (busy) null else onSend,
                icon = { Icon(Icons.AutoMirrored.Rounded.Send) },
                tooltip = if (recording) "完成并发送" else "发送",
                tone = ButtonTone.primary,
                loading = busy,
                size = 34.dp,
            )
        }
    }
}

/**
 * Test-only entry point for the otherwise-private composer attachment strip,
 * so widget tests can render it directly without standing up the whole dock.
 */
@Composable
fun ComposerAttachmentStripForTest(attachments: List<ComposerAttachmentView>, onRemove: (String) -> Unit, onRetry: ((String) -> Unit)? = null) {
    _ComposerAttachmentStrip(attachments = attachments, onRemove = onRemove, onRetry = onRetry ?: {})
}

/**
 * Test-only entry point for the otherwise-private voice panel, so widget
 * tests can render it directly without standing up the whole composer dock.
 */
@Composable
fun VoicePanelForTest(state: VoiceRecorderState, onStart: () -> Unit, onSend: () -> Unit, onCancel: () -> Unit) {
    _VoicePanel(state = state, onStart = onStart, onSend = onSend, onCancel = onCancel)
}

/** Test-only entry point for the otherwise-private sticker panel. */
@Composable
fun StickerPanelForTest(
    state: StickerPanelLoadState,
    onSendSticker: (Sticker) -> Unit,
    onRefresh: () -> Unit,
    onSourceChanged: (StickerPanelSource) -> Unit,
    onUploadFirstPersonalSticker: (() -> Unit)? = null,
    onUploadFirstRoomSticker: (() -> Unit)? = null,
) {
    _StickerPanel(
        state = state,
        onSendSticker = onSendSticker,
        onRefresh = onRefresh,
        onSourceChanged = onSourceChanged,
        onUploadFirstPersonalSticker = onUploadFirstPersonalSticker,
        onUploadFirstRoomSticker = onUploadFirstRoomSticker,
    )
}
