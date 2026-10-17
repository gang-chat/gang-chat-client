package com.gangchat.client.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxQueueItemStatus
import com.gangchat.client.protocol.MusicBoxRequester
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.PersonalMusicPlaylistItem
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.HighlightedText
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiMotion
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.widgets.BoxFit
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FittedBox
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.LocalDefaultTextStyle
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.rememberBuildContext

// Shared list furniture for the music box body: one track row used by the
// request queue, search results, and saved-playlist tracks; one playlist row;
// section headers; and the empty/loading/error states. Keeping a single row
// implementation means a song looks and behaves the same no matter which tab
// it was found in.

internal const val _musicBoxRowMinHeight: Double = 44.0
internal const val _musicBoxRowActionSize: Double = 28.0
internal const val _musicBoxRowLeadingWidth: Double = 24.0

/**
 * Shrinks the font a little when [text] would wrap past [comfortableLines]
 * at [width], so long titles stay fully visible without an ellipsis.
 */
internal fun _musicBoxAdaptiveListTextStyle(
    context: BuildContext,
    text: String,
    baseStyle: TextStyle,
    width: Dp,
    comfortableLines: Int = 2,
    maximumReduction: Double = 2.0,
): TextStyle {
    val baseSize = if (baseStyle.fontSize.isSp) baseStyle.fontSize.value.toDouble() else 13.0
    // Like Dart's TextPainter, the style is measured as given (not merged
    // with the ambient DefaultTextStyle).
    val painter = TextPainter(text, baseStyle).layout(context, maxWidth = width)
    val lineCount = painter.lineCount
    if (lineCount <= comfortableLines) return baseStyle
    val reduction = (lineCount - comfortableLines).coerceIn(1, maximumReduction.toInt()).toDouble()
    return baseStyle.copyWith(fontSize = baseSize - reduction)
}

/**
 * One song in any music-box list. [leading] is the row's status glyph
 * (index number, download spinner, playing waveform); [trailing] holds the
 * list's constant actions. The title area opens the song card
 * ([cardBuilder]).
 */
@Composable
internal fun _MusicBoxTrackRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    query: String = "",
    selected: Boolean = false,
    subtitleColor: Color = UiColors.textSecondary,
    cardBuilder: HoverCardBuilder? = null,
    cardResetKey: Any? = null,
    trailing: List<@Composable () -> Unit> = emptyList(),
    hoverColor: Color = UiColors.surfaceLow,
) = StatefulWidget(
    _MusicBoxTrackRowWidget(leading, title, subtitle, query, selected, subtitleColor, cardBuilder, cardResetKey, trailing, hoverColor, modifier),
) { _MusicBoxTrackRowState() }

private class _MusicBoxTrackRowWidget(
    val leading: @Composable () -> Unit,
    val title: String,
    val subtitle: String,
    val query: String,
    val selected: Boolean,
    val subtitleColor: Color,
    val cardBuilder: HoverCardBuilder?,
    val cardResetKey: Any?,
    val trailing: List<@Composable () -> Unit>,
    val hoverColor: Color,
    val modifier: Modifier,
)

private class _MusicBoxTrackRowState : ViewState<_MusicBoxTrackRowWidget>() {
    private var _hovered = false

    private fun _setHovered(hovered: Boolean) {
        if (_hovered == hovered) return
        setState { _hovered = hovered }
    }

    @Composable
    override fun build() {
        val actionCount = widget.trailing.size
        val titleStyle =
            UiTypography.body.copyWith(
                color = if (widget.selected) UiColors.accent else UiColors.text,
                fontSize = 13.5,
                fontWeight = FontWeight.W600,
                height = 1.3,
            )
        val subtitleStyle = UiTypography.mono.copyWith(color = widget.subtitleColor, fontSize = 10.5)
        val background by animateColorAsState(
            when {
                widget.selected -> UiColors.selected
                _hovered -> widget.hoverColor
                else -> Color.Transparent
            },
            tween(80, easing = Curves.easeOutCubic),
            label = "musicBoxTrackRow",
        )
        val border by animateColorAsState(
            if (widget.selected) UiColors.accentBorder else Color.Transparent,
            tween(80, easing = Curves.easeOutCubic),
            label = "musicBoxTrackRowBorder",
        )
        BoxWithConstraints(
            widget.modifier
                .fillMaxWidth()
                ._liveMouseHover(onEnter = { _setHovered(true) }, onExit = { _setHovered(false) })
                .heightIn(min = _musicBoxRowMinHeight.dp)
                .decoration(background, RoundedCornerShape(UiRadii.md), border)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Dart lays the text out in the row's width less the padding,
            // leading slot, gaps and actions (the row itself is measured with
            // its horizontal padding included).
            val rowWidth = maxWidth + 16.dp
            val textWidth =
                (rowWidth - 16.dp - _musicBoxRowLeadingWidth.dp - 8.dp - (actionCount * (_musicBoxRowActionSize + 2)).dp).coerceAtLeast(24.dp)
            val adaptiveTitle = _musicBoxAdaptiveListTextStyle(context, text = widget.title, baseStyle = titleStyle, width = textWidth)
            val adaptiveSubtitle = _musicBoxAdaptiveListTextStyle(context, text = widget.subtitle, baseStyle = subtitleStyle, width = textWidth)
            val text: @Composable () -> Unit = {
                Column {
                    HighlightedText(text = widget.title, query = widget.query, style = adaptiveTitle)
                    if (widget.subtitle.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        HighlightedText(text = widget.subtitle, query = widget.query, style = adaptiveSubtitle)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(_musicBoxRowLeadingWidth.dp), contentAlignment = Alignment.Center) { widget.leading() }
                Spacer(Modifier.width(8.dp))
                // Dart's Expanded hands the anchor a tight width, so the whole
                // text slot (not just the glyphs) opens the song card.
                Box(Modifier.weight(1f), propagateMinConstraints = true) {
                    val cardBuilder = widget.cardBuilder
                    if (cardBuilder == null) {
                        text()
                    } else {
                        HoverCardAnchor(resetKey = widget.cardResetKey, cardWidth = 310.dp, cardBuilder = cardBuilder) { text() }
                    }
                }
                for (action in widget.trailing) {
                    Spacer(Modifier.width(2.dp))
                    action()
                }
            }
        }
    }
}

/**
 * An icon action inside the scrolling list. Deliberately no tooltip (Dart
 * avoids Material tooltips inside the scrolling list); the label is exposed
 * through semantics instead, so screen readers still announce it.
 */
@Composable
internal fun _MusicBoxRowAction(
    icon: ImageVector,
    label: String,
    onPressed: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.neutral,
    selected: Boolean = false,
    loading: Boolean = false,
    size: Double = _musicBoxRowActionSize,
) {
    Box(modifier.semantics { contentDescription = label }) {
        ButtonIcon(
            icon = { Icon(icon) },
            tone = tone,
            selected = selected,
            loading = loading,
            onPressed = onPressed,
            size = size.dp,
            ghost = tone != ButtonTone.primary,
        )
    }
}

/**
 * The person who requested a song: their avatar with the app's user card on
 * hover/tap. No tooltip here — the card already names the user.
 */
@Composable
internal fun _MusicBoxRequesterAvatar(
    requester: MusicBoxRequester,
    currentUser: CurrentUser?,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    userProfileActionBuilder: UserProfileActionBuilder?,
    modifier: Modifier = Modifier,
    size: Double = 24.0,
) {
    val context = rememberBuildContext()
    UserHoverCard(
        modifier = modifier,
        user =
            UserSummary(
                id = requester.userId,
                username = requester.username,
                displayName = requester.displayName,
                avatarUrl = requester.avatarUrl,
                defaultAvatarKey = requester.defaultAvatarKey,
            ),
        currentUser = currentUser,
        onResolveProfile = onResolveUserProfile,
        onResolveRoomProfile = onResolveRoomProfile,
        onEnterCommonRoom = onEnterCommonRoom,
        profileActionBuilder = userProfileActionBuilder,
        showRoomRole = true,
    ) {
        Avatar(
            modifier = Modifier.semantics { contentDescription = "点歌人：${requester.displayName}" },
            label = requester.avatarLabel,
            imageUrl = AppConfigScope.of(context).resolveAssetUrl(requester.avatarUrl),
            defaultAvatarKey = requester.defaultAvatarKey,
            size = size.dp,
            showBorder = false,
        )
    }
}

/**
 * Leading glyph for a queue row: the 1-based position normally, a spinner
 * while the server is still fetching the file, a waveform for the current
 * track.
 */
@Composable
internal fun _MusicBoxQueueRowLeading(item: MusicBoxQueueItem, index: Int, isCurrent: Boolean) {
    // A download that is already streaming renders like a ready track.
    val loading =
        !item.playable && (item.status == MusicBoxQueueItemStatus.pending || item.status == MusicBoxQueueItemStatus.downloading)
    if (loading) {
        Box(Modifier.size(14.dp).testTag("music-box-queue-leading-loading:${item.id}"), propagateMinConstraints = true) {
            CircularProgressIndicator(strokeWidth = 1.8.dp, color = UiColors.accent)
        }
        return
    }
    if (isCurrent) {
        Icon(
            Icons.Filled.GraphicEq,
            modifier = Modifier.testTag("music-box-queue-leading-playing:${item.id}"),
            size = 18.dp,
            color = UiColors.accent,
            semanticLabel = "正在播放",
        )
        return
    }
    if (item.status == MusicBoxQueueItemStatus.failed) {
        Icon(
            Icons.Filled.ErrorOutline,
            modifier = Modifier.testTag("music-box-queue-leading-failed:${item.id}"),
            size = 16.dp,
            color = UiColors.danger,
        )
        return
    }
    Text(
        "${index + 1}".padStart(2, '0'),
        modifier = Modifier.testTag("music-box-queue-leading-index:${item.id}"),
        style = UiTypography.mono.copyWith(color = UiColors.textMuted, fontSize = 11.0, fontWeight = FontWeight.W700),
    )
}

/**
 * A saved playlist block in the flat list. Tapping the row unfolds its
 * tracks in place; playback starts from a specific song inside, never from
 * the playlist as a whole. The leading icon carries the playlist card
 * (creator, count, 查看/编辑歌单).
 */
@Composable
internal fun _MusicBoxPlaylistRow(
    name: String,
    itemCount: Int,
    playing: Boolean,
    icon: ImageVector,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    open: Boolean = false,
    hoverColor: Color = UiColors.surfaceLow,
    leadingCard: (@Composable (icon: @Composable () -> Unit) -> Unit)? = null,
) = StatefulWidget(_MusicBoxPlaylistRowWidget(name, itemCount, playing, open, icon, onOpen, hoverColor, leadingCard, modifier)) {
    _MusicBoxPlaylistRowState()
}

private class _MusicBoxPlaylistRowWidget(
    val name: String,
    val itemCount: Int,
    val playing: Boolean,
    val open: Boolean,
    val icon: ImageVector,
    val onOpen: () -> Unit,
    val hoverColor: Color,
    val leadingCard: (@Composable (icon: @Composable () -> Unit) -> Unit)?,
    val modifier: Modifier,
)

private class _MusicBoxPlaylistRowState : ViewState<_MusicBoxPlaylistRowWidget>() {
    private var _hovered = false

    private fun _setHovered(hovered: Boolean) {
        if (_hovered == hovered) return
        setState { _hovered = hovered }
    }

    @Composable
    override fun build() {
        val icon: @Composable () -> Unit = {
            Icon(
                if (widget.playing) Icons.Filled.GraphicEq else widget.icon,
                size = 18.dp,
                color = if (widget.playing) UiColors.accent else UiColors.textSecondary,
            )
        }
        val background by animateColorAsState(
            when {
                widget.playing -> UiColors.selected
                _hovered -> widget.hoverColor
                else -> Color.Transparent
            },
            tween(80, easing = Curves.easeOutCubic),
            label = "musicBoxPlaylistRow",
        )
        val border by animateColorAsState(
            if (widget.playing) UiColors.accentBorder else Color.Transparent,
            tween(80, easing = Curves.easeOutCubic),
            label = "musicBoxPlaylistRowBorder",
        )
        Row(
            widget.modifier
                .fillMaxWidth()
                .semantics { contentDescription = widget.name }
                ._liveMouseHover(onEnter = { _setHovered(true) }, onExit = { _setHovered(false) })
                ._liveTap(widget.onOpen)
                .heightIn(min = _musicBoxRowMinHeight.dp)
                .decoration(background, RoundedCornerShape(UiRadii.md), border)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(_musicBoxRowLeadingWidth.dp), contentAlignment = Alignment.Center) {
                val leadingCard = widget.leadingCard
                if (leadingCard == null) icon() else leadingCard(icon)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    widget.name,
                    style =
                        UiTypography.body.copyWith(
                            color = if (widget.playing) UiColors.accent else UiColors.text,
                            fontSize = 13.5,
                            fontWeight = FontWeight.W600,
                        ),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (widget.playing) "正在播放 · ${widget.itemCount} 首" else "${widget.itemCount} 首",
                    style = UiTypography.mono.copyWith(color = UiColors.textMuted, fontSize = 10.5),
                )
            }
            Spacer(Modifier.width(2.dp))
            Icon(if (widget.open) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight, size = 16.dp, color = UiColors.textMuted)
        }
    }
}

/**
 * The compact status line above a list: what it is, how many songs, and a
 * slot for the one contextual action that belongs to the whole list.
 */
@Composable
internal fun _MusicBoxSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: List<@Composable () -> Unit> = emptyList(),
    channel: String? = null,
    channelLit: Boolean = false,
) {
    // Raised buttons paint their hover lift and base depth below the nominal
    // size, so the row must size to its tallest action rather than clip it.
    Row(
        modifier.fillMaxWidth().heightIn(min = _musicBoxControlHeight.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel != null) {
            Spacer(Modifier.width(6.dp))
            Text(channel, style = UiTypography.dot.copyWith(fontSize = 13.0, color = if (channelLit) UiColors.accent else UiColors.textMuted))
            Spacer(Modifier.width(10.dp))
        }
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        _FadeOverflowText(
            title,
            modifier = Modifier.weight(1f),
            style = UiTypography.label.copyWith(color = UiColors.text, fontWeight = FontWeight.W700),
        )
        for (action in trailing) {
            Spacer(Modifier.width(4.dp))
            action()
        }
    }
}

@Composable
internal fun _MusicBoxEmpty(
    icon: ImageVector,
    message: String,
    hint: String? = null,
    actionKey: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FittedBox(fit = BoxFit.scaleDown) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, size = 30.dp, color = UiColors.textMuted)
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    textAlign = TextAlign.Center,
                    style = uiTextStyle(color = UiColors.textMuted, fontSize = 12.0, fontWeight = FontWeight.W600),
                )
                if (hint != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(hint, textAlign = TextAlign.Center, style = uiTextStyle(color = UiColors.textMuted, fontSize = 11.0))
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        modifier = if (actionKey != null) Modifier.testTag(actionKey) else Modifier,
                        icon = { Icon(Icons.Filled.Add) },
                        tone = ButtonTone.primary,
                        height = 34.dp,
                        padding = EdgeInsets.symmetric(horizontal = 13.dp),
                        onPressed = onAction,
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
internal fun _MusicBoxLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = UiColors.accent)
    }
}

@Composable
internal fun _MusicBoxRetryState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.ErrorOutline, size = 28.dp, color = UiColors.textMuted)
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                textAlign = TextAlign.Center,
                style = uiTextStyle(color = UiColors.textMuted, fontSize = 11.0, fontWeight = FontWeight.W600),
            )
            Spacer(Modifier.height(8.dp))
            ButtonIcon(
                modifier = Modifier.testTag("music-box-retry"),
                icon = { Icon(Icons.Filled.Refresh) },
                tooltip = "重试",
                onPressed = onRetry,
                size = 30.dp,
            )
        }
    }
}

internal fun _musicBoxPlaylistItemAsResult(item: PersonalMusicPlaylistItem): MusicBoxSearchResult =
    MusicBoxSearchResult(trackId = item.trackId, name = item.title, artists = item.artists, source = item.source)

/**
 * Flutter's single-line `Text(maxLines: 1, softWrap: false, overflow:
 * TextOverflow.fade)`: when the line overflows, its last ellipsis-width fades
 * out instead of being cut or ellipsized.
 */
@Composable
internal fun _FadeOverflowText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val context = rememberBuildContext()
    val density = LocalDensity.current
    var overflowing by remember { mutableStateOf(false) }
    val merged = LocalDefaultTextStyle.current.merge(style)
    val fadeWidth = TextPainter("…", merged, maxLines = 1).layout(context).width
    Text(
        text,
        modifier =
            modifier
                .clipToBounds()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    if (overflowing) {
                        val fadeStart = size.width - with(density) { fadeWidth.dp.toPx() }
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = fadeStart, endX = size.width),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = textAlign,
        style = style,
        onTextLayout = { layout -> overflowing = layout.didOverflowWidth },
    )
}
