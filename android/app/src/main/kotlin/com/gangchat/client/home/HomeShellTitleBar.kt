package com.gangchat.client.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.GlobalSearchCategory
import com.gangchat.client.app.JoinedLiveRoomSummary
import com.gangchat.client.app.PublicRoomJoinCandidate
import com.gangchat.client.app.globalSearchCategories
import com.gangchat.client.app.globalSearchCategoryCount
import com.gangchat.client.app.globalSearchCategoryLabel
import com.gangchat.client.app.globalSearchFileSubtitle
import com.gangchat.client.app.globalSearchFileTitle
import com.gangchat.client.app.globalSearchHasResults
import com.gangchat.client.app.globalSearchMessageSubtitle
import com.gangchat.client.app.globalSearchMessageTitle
import com.gangchat.client.app.globalSearchResultTimeLabel
import com.gangchat.client.app.liveMicControlState
import com.gangchat.client.app.publicRoomJoinCandidates
import com.gangchat.client.app.roomCardAvatarLabel
import com.gangchat.client.app.roomSidebarLastMessageTime
import com.gangchat.client.app.roomSidebarSubtitle
import com.gangchat.client.app.superuserGlobalSearchCategories
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.GlobalSearchResults
import com.gangchat.client.protocol.MessageSearchResult
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.CompactCategoryButton
import com.gangchat.client.ui.FloatingNotice
import com.gangchat.client.ui.FloatingNoticeEmitter
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.HighlightedText
import com.gangchat.client.ui.OverflowMarqueeText
import com.gangchat.client.ui.TextFieldContextMenu
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTooltip
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui._EditableTextField
import com.gangchat.client.ui.copyWith
import com.gangchat.client.widgets.BoxShadow
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.LocalDefaultTextStyle
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.TapRegion
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.UndoHistoryController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.rememberBuildContext
import java.text.BreakIterator

// Port of lib/src/home/home_shell_title_bar.dart, limited to what Android
// shows: the 44 dp title bar strip with (on tablets) the centred search field
// and the live room dock on its right, the live room dock in the narrow room
// list's footer, the search field (also used in the narrow room list's
// header) and the search results panel.
//
// PORT: the desktop window chrome is not ported: the window controls, the
// drag regions and double-click maximise, and the macOS traffic-light and
// Windows/Linux layout variants (Android places the dock on the right and
// shows no window controls).

internal val _homeTitleBarHeight: Dp = 44.dp
private val _homeTitleBarSearchWidth: Dp = 520.dp
private val _homeTitleBarSearchHeight: Dp = 30.dp
private val _homeTitleBarMinSearchWidth: Dp = 122.dp
private val _homeTitleBarLiveRoomHeight: Dp = 30.dp
private val _homeTitleBarLiveRoomHorizontalInset: Dp = 10.dp
private val _homeTitleBarLiveRoomDefaultWidth: Dp = sidebarWidth - _homeTitleBarLiveRoomHorizontalInset * 2
private val _homeTitleBarLiveRoomSearchGap: Dp = 14.dp
private val _homeTitleBarLiveRoomActionSize: Dp = 28.dp
private val _homeTitleBarLiveRoomMinActionSize: Dp = 16.dp
private val _homeTitleBarLiveRoomMinWidth: Dp = 80.dp
private const val _homeTitleBarLiveRoomTextSafetyWidth = 2.0

/** Dart's `_TitleLiveRoomDockBuilder` (`Widget Function(double maxWidth, {bool fillAvailable})`). */
internal typealias _TitleLiveRoomDockBuilder = @Composable (maxWidth: Dp, fillAvailable: Boolean) -> Unit

// Test tags of the live room dock (Dart's `ValueKey`s).
const val homeTitleLiveRoomTag = "home-title-live-room"
const val homeTitleLiveRoomNameTag = "home-title-live-room:name"
const val homeTitleLiveRoomMarqueeTrackTag = "home-title-live-room:marquee-track"
const val homeTitleLiveRoomMicTag = "home-title-live-room:mic"
const val homeTitleLiveRoomHeadphonesTag = "home-title-live-room:headphones"
const val homeTitleLiveRoomLeaveTag = "home-title-live-room:leave"

/** Test tag of the title bar strip. */
const val homeTitleBarTag = "home-title-bar"

/** Test tag of a search category tab (Dart's `ValueKey('search-category-${category.name}')`). */
fun searchCategoryTag(category: GlobalSearchCategory) = "search-category-${category.name}"

fun myRoomActionTag(roomId: String) = "my-room-action-$roomId"

fun publicRoomActionTag(roomId: String) = "public-room-action-$roomId"

fun openUserSettingsTag(userId: String) = "open-user-settings-$userId"

fun searchMessageResultTag(messageId: String) = "search-message-result-$messageId"

/** Test tag of the search results scroll view. */
const val homeTitleSearchResultsListTag = "home-title-search-results-list"

private fun _homeTitleBarCanShowSearch(maxWidth: Dp): Boolean = maxWidth >= narrowBreakpoint

internal class _HomeTitleBarSearchLayout(val left: Dp, val width: Dp) {
    val right: Dp get() = left + width
}

/**
 * Where the title bar puts its search field. Android (no window controls)
 * places the live room on the right, so the field centres between the left
 * edge and the space the dock reserves.
 */
internal fun _homeTitleBarSearchLayout(maxWidth: Dp, hasLiveRoom: Boolean): _HomeTitleBarSearchLayout? {
    if (!_homeTitleBarCanShowSearch(maxWidth)) return null

    val placeLiveRoomOnRight = true
    val liveRoomLeft = _homeTitleBarLiveRoomHorizontalInset
    var minimumLeft = 0.dp
    var maximumRight = maxWidth
    if (hasLiveRoom) {
        val reservedLiveRoomWidth =
            _homeTitleBarLiveRoomHorizontalInset + _homeTitleBarLiveRoomMinWidth + _homeTitleBarLiveRoomSearchGap
        if (placeLiveRoomOnRight) {
            maximumRight -= reservedLiveRoomWidth
        } else {
            minimumLeft = liveRoomLeft + _homeTitleBarLiveRoomMinWidth + _homeTitleBarLiveRoomSearchGap
        }
    }

    val availableWidth = maximumRight - minimumLeft
    val width = availableWidth.coerceIn(_homeTitleBarMinSearchWidth, _homeTitleBarSearchWidth)
    val centeredLeft = (maxWidth - width) / 2
    if (hasLiveRoom && placeLiveRoomOnRight) {
        val preferredRight =
            maxWidth - _homeTitleBarLiveRoomHorizontalInset - _homeTitleBarLiveRoomDefaultWidth - _homeTitleBarLiveRoomSearchGap
        if (preferredRight - width >= minimumLeft) {
            maximumRight = preferredRight
        }
    }
    val left = centeredLeft.coerceIn(minimumLeft, maximumRight - width)
    return _HomeTitleBarSearchLayout(left = left, width = width)
}

@Composable
internal fun _HomeTitleBar(
    searchController: TextEditingController,
    searchTapRegionGroup: Any,
    liveRoomDockBuilder: _TitleLiveRoomDockBuilder,
    interactionLocked: Boolean,
    onActivateSearch: () -> Unit,
    onSearchTapOutside: () -> Unit,
    onSearchContextMenuOpenChanged: (Boolean) -> Unit,
    onClearSearchQuery: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(_homeTitleBarHeight)
            .testTag(homeTitleBarTag)
            .decoration(UiColors.surfaceLow)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRect(UiColors.border, topLeft = Offset(0f, size.height - stroke), size = Size(size.width, stroke))
            },
    ) {
        val searchLayout = _homeTitleBarSearchLayout(maxWidth, hasLiveRoom = true)
        val narrow = maxWidth < narrowBreakpoint
        // Android phones show no title-bar live room: it moves to the room
        // list's footer.
        val showLiveRoom = !narrow
        val liveRoomLeft = _homeTitleBarLiveRoomHorizontalInset
        val liveRoomBoundary = maxWidth
        val liveRoomAvailableWidth =
            if (searchLayout == null) {
                liveRoomBoundary - liveRoomLeft - _homeTitleBarLiveRoomHorizontalInset
            } else {
                maxWidth - _homeTitleBarLiveRoomHorizontalInset - searchLayout.right - _homeTitleBarLiveRoomSearchGap
            }
        val liveRoomMaxWidth = liveRoomAvailableWidth.coerceAtLeast(0.dp)

        if (showLiveRoom) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = (_homeTitleBarHeight - _homeTitleBarLiveRoomHeight) / 2,
                        end = _homeTitleBarLiveRoomHorizontalInset,
                    ),
            ) {
                liveRoomDockBuilder(liveRoomMaxWidth, false)
            }
        }
        if (searchLayout != null) {
            Box(
                Modifier
                    .offset(x = searchLayout.left, y = (_homeTitleBarHeight - _homeTitleBarSearchHeight) / 2)
                    .width(searchLayout.width),
            ) {
                TapRegion(groupId = searchTapRegionGroup, onTapOutside = { onSearchTapOutside() }) {
                    Box(Modifier.fillMaxWidth().height(_homeTitleBarSearchHeight).testTag(homeTitleSearchTag), propagateMinConstraints = true) {
                        _TitleSearchField(
                            controller = searchController,
                            tapRegionGroup = searchTapRegionGroup,
                            enabled = !interactionLocked,
                            onContextMenuOpenChanged = onSearchContextMenuOpenChanged,
                            onActivated = onActivateSearch,
                            onClearQuery = onClearSearchQuery,
                        )
                    }
                }
            }
        }
    }
}

private fun _titleLiveTextCanShowCharacter(context: BuildContext, text: String, style: TextStyle, maxWidth: Dp): Boolean {
    if (text.isEmpty() || maxWidth <= 0.dp) return false
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val firstEnd = iterator.next()
    val first = if (firstEnd == BreakIterator.DONE) text else text.substring(0, firstEnd)
    return _titleLiveRoomTextWidth(context, text = first, style = style) <= maxWidth.value.toDouble()
}

private fun _titleLiveRoomTextWidth(context: BuildContext, text: String, style: TextStyle): Double {
    val painter = TextPainter(text, style, maxLines = 1).layout(context)
    return kotlin.math.ceil(painter.width) + _homeTitleBarLiveRoomTextSafetyWidth
}

@Composable
internal fun _TitleLiveRoomDock(
    maxWidth: Dp,
    fillAvailable: Boolean = false,
    room: JoinedLiveRoomSummary?,
    micMuted: Boolean,
    headphonesMuted: Boolean,
    voiceBlocked: Boolean,
    onOpen: (() -> Unit)?,
    onToggleMic: (() -> Unit)?,
    onToggleHeadphones: () -> Unit,
    onLeave: (() -> Unit)?,
    interactionLocked: Boolean,
) {
    val context = rememberBuildContext()
    val namedLeadingInset = 7.dp
    val compactLeadingInset = 6.dp
    val trailingInset = 4.dp
    val volumeIconSize = 16.dp
    val identityGap = 7.dp
    val avatarSize = 20.dp
    val nameActionGap = 8.dp
    val joined = room != null
    val label = room?.displayName ?: "未加入语音频道"
    val actionCount = if (joined) 3 else 2
    val micControl = liveMicControlState(micMuted = micMuted, voiceBlocked = voiceBlocked)
    val resolvedAvatar = if (room != null) AppConfigScope.of(context).resolveAssetUrl(room.avatarUrl) else null
    val roomNameStyle =
        LocalDefaultTextStyle.current.merge(UiTypography.label.copyWith(color = UiColors.text, fontWeight = FontWeight.W600))
    val fullRoomNameWidth = _titleLiveRoomTextWidth(context, text = label, style = roomNameStyle)
    val preferredIdentityWidth = if (joined) volumeIconSize + identityGap + avatarSize + identityGap else 0.dp
    val preferredWidth =
        (
            namedLeadingInset +
                preferredIdentityWidth +
                fullRoomNameWidth.dp +
                nameActionGap +
                _homeTitleBarLiveRoomActionSize * actionCount +
                trailingInset
        ).coerceAtLeast(_homeTitleBarLiveRoomDefaultWidth)
    val width = if (fillAvailable) maxWidth else preferredWidth.coerceIn(0.dp, maxWidth)
    val actionSize =
        ((width - (if (joined) 32.dp else 20.dp)) / actionCount)
            .coerceIn(_homeTitleBarLiveRoomMinActionSize, _homeTitleBarLiveRoomActionSize)
    val actionButtonsWidth = actionSize * actionCount
    val namedIdentityFixedWidth =
        namedLeadingInset + (if (joined) preferredIdentityWidth else 0.dp) + nameActionGap + actionButtonsWidth + trailingInset
    val showRoomName =
        _titleLiveTextCanShowCharacter(context, text = label, style = roomNameStyle, maxWidth = width - namedIdentityFixedWidth)
    val volumeIdentityWidth = compactLeadingInset + volumeIconSize + identityGap + avatarSize + actionButtonsWidth + trailingInset
    val showVolumeIcon = joined && (showRoomName || width >= volumeIdentityWidth)
    val shape = RoundedCornerShape(UiRadii.md)

    val dock: @Composable () -> Unit = {
        Row(
            Modifier
                .width(width)
                .height(_homeTitleBarLiveRoomHeight)
                .testTag(homeTitleLiveRoomTag)
                .then(
                    if (!interactionLocked && onOpen != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpen,
                        )
                    } else {
                        Modifier
                    },
                )
                .decoration(UiColors.surface, shape, UiColors.border)
                .padding(start = if (showRoomName) namedLeadingInset else compactLeadingInset, end = trailingInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showVolumeIcon) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, size = volumeIconSize, color = UiColors.accent)
                Spacer(Modifier.width(identityGap))
            }
            if (room != null) {
                Avatar(
                    label = room.avatarLabel,
                    imageUrl = resolvedAvatar,
                    defaultAvatarKey = room.defaultAvatarKey,
                    size = avatarSize,
                    showBorder = false,
                )
            }
            if (showRoomName) {
                if (joined) Spacer(Modifier.width(identityGap))
                OverflowMarqueeText(
                    text = label,
                    style = roomNameStyle,
                    modifier = Modifier.weight(1f).testTag(homeTitleLiveRoomNameTag),
                    trackKey = homeTitleLiveRoomMarqueeTrackTag,
                )
                Spacer(Modifier.width(nameActionGap))
            } else {
                Spacer(Modifier.weight(1f))
            }
            _TitleLiveActionButton(
                tag = homeTitleLiveRoomMicTag,
                size = actionSize,
                tooltip = if (micControl.mutedForDisplay) "打开麦克风" else "关闭麦克风",
                icon = if (micControl.mutedForDisplay) Icons.Filled.MicOff else Icons.Filled.Mic,
                color =
                    when {
                        !micControl.enabled -> UiColors.danger
                        micControl.active -> UiColors.accent
                        else -> UiColors.textMuted
                    },
                onPressed = if (micControl.enabled) onToggleMic else null,
                interactionLocked = interactionLocked,
            )
            _TitleLiveActionButton(
                tag = homeTitleLiveRoomHeadphonesTag,
                size = actionSize,
                tooltip = if (headphonesMuted) "打开耳机" else "关闭耳机",
                icon = if (headphonesMuted) Icons.Filled.HeadsetOff else Icons.Filled.Headphones,
                color = if (headphonesMuted) UiColors.textMuted else UiColors.accent,
                onPressed = onToggleHeadphones,
                interactionLocked = interactionLocked,
            )
            if (joined) {
                _TitleLiveActionButton(
                    tag = homeTitleLiveRoomLeaveTag,
                    size = actionSize,
                    tooltip = "离开语音频道",
                    icon = Icons.Filled.CallEnd,
                    color = UiColors.danger,
                    onPressed = onLeave,
                    interactionLocked = interactionLocked,
                )
            }
        }
    }
    // Dart's IgnorePointer(ignoring: interactionLocked): no tooltip and no
    // taps while an app update downloads.
    Box(Modifier.alpha(if (interactionLocked) 0.54f else 1f)) {
        if (interactionLocked) {
            dock()
        } else {
            UiTooltip(
                message = if (joined) "打开语音频道" else "可在加入前设置麦克风和耳机",
                preferBelow = true,
                verticalOffset = 22.dp,
            ) {
                dock()
            }
        }
    }
}

@Composable
private fun _TitleLiveActionButton(
    tag: String,
    tooltip: String,
    icon: ImageVector,
    color: Color,
    onPressed: (() -> Unit)?,
    size: Dp,
    interactionLocked: Boolean,
) {
    val enabled = onPressed != null
    val content: @Composable () -> Unit = {
        Box(
            Modifier
                .size(size)
                .testTag(tag)
                .then(
                    if (interactionLocked) {
                        Modifier
                    } else {
                        // Dart's opaque GestureDetector: a tap is consumed even
                        // when the action is disabled.
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (enabled) onPressed!!() },
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                size = (size - 8.dp).coerceIn(10.dp, 16.dp),
                color = if (enabled) color else color.copy(alpha = 0.56f),
            )
        }
    }
    if (interactionLocked) {
        content()
    } else {
        UiTooltip(message = tooltip, preferBelow = true, verticalOffset = 22.dp) { content() }
    }
}

/**
 * 标题栏中央的搜索框。不复用通用 Input 封装,而是一个普通的圆角矩形,
 * 获得焦点时会像按钮一样显示绿色边框与底色。
 */
@Composable
internal fun _TitleSearchField(
    controller: TextEditingController,
    tapRegionGroup: Any,
    enabled: Boolean,
    onContextMenuOpenChanged: (Boolean) -> Unit,
    onActivated: () -> Unit,
    onClearQuery: () -> Unit,
) = StatefulWidget(
    _TitleSearchFieldWidget(controller, tapRegionGroup, enabled, onContextMenuOpenChanged, onActivated, onClearQuery),
) { _TitleSearchFieldState() }

internal class _TitleSearchFieldWidget(
    val controller: TextEditingController,
    val tapRegionGroup: Any,
    val enabled: Boolean,
    val onContextMenuOpenChanged: (Boolean) -> Unit,
    val onActivated: () -> Unit,
    val onClearQuery: () -> Unit,
)

private class _TitleSearchFieldState : ViewState<_TitleSearchFieldWidget>() {
    private val _focusNode = FocusNode()
    private val _undoController = UndoHistoryController()
    private var _focused = false
    private var _hasText = false
    private val _textListener: () -> Unit = { _handleTextChanged() }
    private val _focusListener: () -> Unit = { _handleFocusChange() }

    override fun initState() {
        _hasText = widget.controller.text.trim().isNotEmpty()
        widget.controller.addListener(_textListener)
        _focusNode.addListener(_focusListener)
    }

    override fun didUpdateWidget(oldWidget: _TitleSearchFieldWidget) {
        if (oldWidget.enabled && !widget.enabled) {
            _focusNode.unfocus()
        }
        if (oldWidget.controller === widget.controller) return
        oldWidget.controller.removeListener(_textListener)
        _hasText = widget.controller.text.trim().isNotEmpty()
        widget.controller.addListener(_textListener)
    }

    override fun dispose() {
        widget.controller.removeListener(_textListener)
        _focusNode.removeListener(_focusListener)
        _focusNode.dispose()
        _undoController.dispose()
    }

    private fun _handleFocusChange() {
        if (_focusNode.hasFocus && widget.enabled) widget.onActivated()
        if (_focused != _focusNode.hasFocus) {
            setState { _focused = _focusNode.hasFocus }
        }
    }

    private fun _handleTextChanged() {
        val hasText = widget.controller.text.trim().isNotEmpty()
        if (_hasText == hasText) return
        setState { _hasText = hasText }
    }

    @Composable
    override fun build() {
        val activeFocus = _focused && widget.enabled
        val accent = if (activeFocus) UiColors.accent else UiColors.textMuted
        val animation = tween<Color>(90, easing = Curves.easeOutCubic)
        val background by animateColorAsState(if (activeFocus) UiColors.selected else UiColors.surface, animation, label = "titleSearchBackground")
        val border by animateColorAsState(if (activeFocus) UiColors.selectedBorder else UiColors.border, animation, label = "titleSearchBorder")
        val textStyle = UiTypography.body.copyWith(fontSize = 13.0, height = 1.2)
        val hintStyle = UiTypography.body.copyWith(fontSize = 13.0, height = 1.2, color = UiColors.textMuted)
        Row(
            Modifier
                .fillMaxWidth()
                .height(_homeTitleBarSearchHeight)
                .decoration(background, RoundedCornerShape(UiRadii.md), border)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, size = 16.dp, color = accent)
            Spacer(Modifier.width(8.dp))
            _EditableTextField(
                controller = widget.controller,
                focusNode = _focusNode,
                undoController = _undoController,
                style = textStyle,
                contextMenu =
                    TextFieldContextMenu(
                        undoController = _undoController,
                        tapRegionGroupId = widget.tapRegionGroup,
                        onOpenChanged = widget.onContextMenuOpenChanged,
                    ),
                // Dart's `TextField.onTap`: a tap on the (possibly already
                // focused) field reopens the results.
                modifier =
                    Modifier.weight(1f).then(
                        if (widget.enabled) {
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    val up = waitForUpOrCancellation(PointerEventPass.Initial)
                                    if (up != null && widget.enabled) widget.onActivated()
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
                enabled = widget.enabled,
                textInputAction = ImeAction.Search,
                maxLines = 1,
            ) { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (widget.controller.text.isEmpty()) {
                        Text("搜索", style = hintStyle, maxLines = 1, softWrap = false)
                    }
                    innerTextField()
                }
            }
            if (_hasText && widget.enabled) {
                Spacer(Modifier.width(6.dp))
                UiTooltip(message = "清空搜索") {
                    Box(
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = widget.onClearQuery,
                        ),
                    ) {
                        Icon(Icons.Filled.Close, size = 15.dp, color = UiColors.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
internal fun _TitleSearchResultsPanel(
    query: String,
    results: GlobalSearchResults?,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    timestampNow: DateTime,
    currentUser: CurrentUser,
    activeCategory: GlobalSearchCategory?,
    visibleCategories: List<GlobalSearchCategory>,
    busyPublicRoomId: String?,
    pendingPublicRoomIds: Set<String>,
    onCategorySelected: (GlobalSearchCategory) -> Unit,
    onLoadMore: () -> Unit,
    onMyRoomSelected: (RoomCard) -> Unit,
    onProfileRoomSelected: (PublicRoom) -> Unit,
    onResolveRoomProfile: RoomProfileResolver,
    onResolveRoomUserProfile: RoomUserProfileResolver,
    onResolveUserProfile: UserProfileResolver,
    onPublicRoomAction: (PublicRoom) -> Unit,
    onUserSettingsSelected: (UserSummary) -> Unit,
    onMessageSelected: (MessageSearchResult) -> Unit,
    onFileSelected: (MessageSearchResult) -> Unit,
) {
    val message = error?.trim()
    val shape = RoundedCornerShape(UiRadii.lg)
    val panel: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .boxShadow(listOf(BoxShadow(color = Color.Black.copy(alpha = 0.34f), blurRadius = 18.dp, offset = DpOffset(0.dp, 8.dp))), shape)
                .decoration(UiColors.surfaceLow, shape, UiColors.borderStrong)
                .clip(shape)
                .heightIn(max = 440.dp),
        ) {
            _SearchCategoryTabs(
                results = results,
                activeCategory = activeCategory,
                categories = if (currentUser.isSuperuser) superuserGlobalSearchCategories else globalSearchCategories,
                onCategorySelected = onCategorySelected,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).decoration(UiColors.border))
            Box(Modifier.weight(1f, fill = false)) {
                _SearchResultsBody(
                    query = query,
                    results = results,
                    loading = loading,
                    loadingMore = loadingMore,
                    error = error,
                    timestampNow = timestampNow,
                    currentUser = currentUser,
                    activeCategory = activeCategory,
                    visibleCategories = visibleCategories,
                    busyPublicRoomId = busyPublicRoomId,
                    pendingPublicRoomIds = pendingPublicRoomIds,
                    onLoadMore = onLoadMore,
                    onMyRoomSelected = onMyRoomSelected,
                    onProfileRoomSelected = onProfileRoomSelected,
                    onResolveRoomProfile = onResolveRoomProfile,
                    onResolveRoomUserProfile = onResolveRoomUserProfile,
                    onResolveUserProfile = onResolveUserProfile,
                    onPublicRoomAction = onPublicRoomAction,
                    onUserSettingsSelected = onUserSettingsSelected,
                    onMessageSelected = onMessageSelected,
                    onFileSelected = onFileSelected,
                )
            }
        }
    }
    if (message == null || message.isEmpty()) {
        panel()
        return
    }
    FloatingNoticeEmitter(
        notices = listOf(FloatingNotice(message = message, tone = FloatingNoticeTone.error, duration = null)),
        child = panel,
    )
}

@Composable
private fun _SearchResultsBody(
    query: String,
    results: GlobalSearchResults?,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    timestampNow: DateTime,
    currentUser: CurrentUser,
    activeCategory: GlobalSearchCategory?,
    visibleCategories: List<GlobalSearchCategory>,
    busyPublicRoomId: String?,
    pendingPublicRoomIds: Set<String>,
    onLoadMore: () -> Unit,
    onMyRoomSelected: (RoomCard) -> Unit,
    onProfileRoomSelected: (PublicRoom) -> Unit,
    onResolveRoomProfile: RoomProfileResolver,
    onResolveRoomUserProfile: RoomUserProfileResolver,
    onResolveUserProfile: UserProfileResolver,
    onPublicRoomAction: (PublicRoom) -> Unit,
    onUserSettingsSelected: (UserSummary) -> Unit,
    onMessageSelected: (MessageSearchResult) -> Unit,
    onFileSelected: (MessageSearchResult) -> Unit,
) {
    if (error != null) {
        _SearchPanelState(icon = Icons.Filled.ErrorOutline, title = "搜索失败", detail = "请稍后重试")
        return
    }

    if (results == null && loading) {
        Box(Modifier.fillMaxWidth().height(104.dp), contentAlignment = Alignment.Center) {
            _ChatProgressIndicator(size = 22.dp, strokeWidth = 2.2.dp, color = UiColors.accent)
        }
        return
    }

    if (!globalSearchHasResults(results)) {
        _SearchPanelState(icon = Icons.Filled.Search, title = "没有找到结果", detail = query.trim())
        return
    }
    val snapshot = results!!

    val sections = visibleCategories.filter { category -> globalSearchCategoryCount(snapshot, category) != 0 }
    if (sections.isEmpty()) {
        _SearchPanelState(
            icon = Icons.Outlined.FilterAlt,
            title = "该分类没有结果",
            detail = if (activeCategory == null) query.trim() else globalSearchCategoryLabel(activeCategory),
        )
        return
    }

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val currentLoadingMore = androidx.compose.runtime.rememberUpdatedState(loadingMore)
    val currentOnLoadMore = androidx.compose.runtime.rememberUpdatedState(onLoadMore)
    // Dart's NotificationListener<ScrollNotification>: near the end of a
    // scrollable list, ask for the next cursor page.
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { value ->
            if (currentLoadingMore.value) return@collect
            if (scrollState.maxValue <= 0) return@collect
            val extentAfter = (scrollState.maxValue - value) / density.density
            if (extentAfter <= 96f) currentOnLoadMore.value()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(homeTitleSearchResultsListTag)
            .verticalScroll(scrollState)
            .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 12.dp),
    ) {
        for ((index, category) in sections.withIndex()) {
            if (index > 0) Spacer(Modifier.height(10.dp))
            _SearchResultSection(
                title = globalSearchCategoryLabel(category),
                count = globalSearchCategoryCount(snapshot, category),
            ) {
                _searchSectionChildren(
                    snapshot = snapshot,
                    category = category,
                    query = query,
                    timestampNow = timestampNow,
                    currentUser = currentUser,
                    busyPublicRoomId = busyPublicRoomId,
                    pendingPublicRoomIds = pendingPublicRoomIds,
                    onMyRoomSelected = onMyRoomSelected,
                    onProfileRoomSelected = onProfileRoomSelected,
                    onResolveRoomProfile = onResolveRoomProfile,
                    onResolveRoomUserProfile = onResolveRoomUserProfile,
                    onResolveUserProfile = onResolveUserProfile,
                    onPublicRoomAction = onPublicRoomAction,
                    onUserSettingsSelected = onUserSettingsSelected,
                    onMessageSelected = onMessageSelected,
                    onFileSelected = onFileSelected,
                )
            }
        }
        if (loadingMore) _SearchLoadingMoreIndicator()
    }
}

@Composable
private fun _searchSectionChildren(
    snapshot: GlobalSearchResults,
    category: GlobalSearchCategory,
    query: String,
    timestampNow: DateTime,
    currentUser: CurrentUser,
    busyPublicRoomId: String?,
    pendingPublicRoomIds: Set<String>,
    onMyRoomSelected: (RoomCard) -> Unit,
    onProfileRoomSelected: (PublicRoom) -> Unit,
    onResolveRoomProfile: RoomProfileResolver,
    onResolveRoomUserProfile: RoomUserProfileResolver,
    onResolveUserProfile: UserProfileResolver,
    onPublicRoomAction: (PublicRoom) -> Unit,
    onUserSettingsSelected: (UserSummary) -> Unit,
    onMessageSelected: (MessageSearchResult) -> Unit,
    onFileSelected: (MessageSearchResult) -> Unit,
) {
    val context = rememberBuildContext()
    val config = AppConfigScope.of(context)
    when (category) {
        GlobalSearchCategory.myRooms ->
            for (room in snapshot.myRooms) {
                val time = roomSidebarLastMessageTime(room, now = timestampNow)
                _SearchResultTile(
                    title = room.displayName,
                    subtitle = roomSidebarSubtitle(room),
                    query = query,
                    leading = {
                        RoomHoverCard(
                            room = _publicRoomFromRoomCard(room),
                            currentUser = currentUser,
                            onResolveRoom = onResolveRoomProfile,
                            onResolveUserProfile = { user -> onResolveRoomUserProfile(room.id, user) },
                            onEnterRoom = onProfileRoomSelected,
                        ) {
                            Avatar(
                                label = roomCardAvatarLabel(room),
                                imageUrl = config.resolveAssetUrl(room.avatarUrl),
                                defaultAvatarKey = room.defaultAvatarKey,
                                size = 30.dp,
                            )
                        }
                    },
                    titleTrailing =
                        if (time.isEmpty()) {
                            null
                        } else {
                            {
                                Text(
                                    time,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = UiTypography.label.copyWith(color = UiColors.textMuted, fontSize = 10.5),
                                )
                            }
                        },
                    trailing = { _MyRoomEnterActivity(room = room, onPressed = onMyRoomSelected) },
                )
            }
        GlobalSearchCategory.publicRooms ->
            for (candidate in publicRoomJoinCandidates(
                rooms = snapshot.publicRooms,
                pendingRoomIds = pendingPublicRoomIds,
                busyRoomId = busyPublicRoomId,
            )) {
                _SearchResultTile(
                    title = candidate.room.name,
                    subtitle = _publicRoomSearchMeta(candidate.room),
                    query = query,
                    leading = {
                        RoomHoverCard(
                            room = candidate.room,
                            currentUser = currentUser,
                            onResolveRoom = onResolveRoomProfile,
                            onResolveUserProfile = { user -> onResolveRoomUserProfile(candidate.room.id, user) },
                            onEnterRoom = if (candidate.room.joined) onProfileRoomSelected else null,
                        ) {
                            Avatar(
                                label = candidate.room.name,
                                imageUrl = config.resolveAssetUrl(candidate.room.avatarUrl),
                                defaultAvatarKey = candidate.room.defaultAvatarKey,
                                size = 30.dp,
                            )
                        }
                    },
                    trailing = { _PublicRoomJoinActivity(candidate = candidate, onPressed = onPublicRoomAction) },
                )
            }
        GlobalSearchCategory.userSettings ->
            for (user in snapshot.userSettings) {
                _SearchResultTile(
                    title = if (user.displayName.trim().isEmpty()) user.username else user.displayName,
                    subtitle = "@${user.username}",
                    query = query,
                    leading = {
                        UserHoverCard(user = user, currentUser = currentUser, onResolveProfile = onResolveUserProfile) {
                            Avatar(
                                label = user.displayName,
                                imageUrl = config.resolveAssetUrl(user.avatarUrl),
                                defaultAvatarKey = user.defaultAvatarKey,
                                size = 30.dp,
                            )
                        }
                    },
                    trailing = {
                        Button(
                            modifier = Modifier.testTag(openUserSettingsTag(user.id)),
                            width = _searchResultActionWidth(context, hasIcon = true),
                            height = 30.dp,
                            padding = _searchResultActionPadding(context, wideHorizontal = 16.dp),
                            onPressed = { onUserSettingsSelected(user) },
                            icon = { Icon(Icons.Outlined.ManageAccounts, size = 15.dp) },
                        ) {
                            Text("进入设置")
                        }
                    },
                )
            }
        GlobalSearchCategory.messages ->
            for (result in snapshot.messages) {
                _MessageSearchResultTile(
                    result = result,
                    title = globalSearchMessageTitle(result),
                    subtitle = globalSearchMessageSubtitle(result),
                    query = query,
                    onPressed = { onMessageSelected(result) },
                )
            }
        GlobalSearchCategory.files ->
            for (result in snapshot.files) {
                _MessageSearchResultTile(
                    result = result,
                    title = globalSearchFileTitle(result),
                    subtitle = globalSearchFileSubtitle(result),
                    query = query,
                    onPressed = { onFileSelected(result) },
                )
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun _SearchCategoryTabs(
    results: GlobalSearchResults?,
    activeCategory: GlobalSearchCategory?,
    categories: List<GlobalSearchCategory>,
    onCategorySelected: (GlobalSearchCategory) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp)) {
        val compact = maxWidth < 380.dp
        val columns = if (compact) 2 else 4
        val itemWidth = ((maxWidth - 6.dp * (columns - 1)) / columns).coerceIn(0.dp, maxWidth)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (category in categories) {
                Box(Modifier.width(itemWidth), propagateMinConstraints = true) {
                    CompactCategoryButton(
                        modifier = Modifier.testTag(searchCategoryTag(category)),
                        label = globalSearchCategoryLabel(category),
                        count = globalSearchCategoryCount(results, category),
                        selected = activeCategory == category,
                        onPressed = { onCategorySelected(category) },
                    )
                }
            }
        }
    }
}

@Composable
private fun _SearchResultSection(title: String, count: Int, children: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "$title $count",
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 7.dp),
            style = UiTypography.label.copy(color = UiColors.textMuted),
        )
        children()
    }
}

@Composable
private fun _SearchLoadingMoreIndicator() {
    Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) {
        _ChatProgressIndicator(size = 16.dp, strokeWidth = 2.dp, color = UiColors.accent)
    }
}

@Composable
private fun _MyRoomEnterActivity(room: RoomCard, onPressed: (RoomCard) -> Unit) {
    val context = rememberBuildContext()
    Button(
        modifier = Modifier.testTag(myRoomActionTag(room.id)),
        width = _searchResultActionWidth(context, hasIcon = false),
        height = 30.dp,
        padding = _searchResultActionPadding(context, wideHorizontal = 10.dp),
        tone = ButtonTone.primary,
        onPressed = { onPressed(room) },
    ) {
        Text("进入房间")
    }
}

@Composable
private fun _PublicRoomJoinActivity(candidate: PublicRoomJoinCandidate, onPressed: (PublicRoom) -> Unit) {
    val context = rememberBuildContext()
    val room = candidate.room
    Button(
        modifier = Modifier.testTag(publicRoomActionTag(room.id)),
        width = _searchResultActionWidth(context, hasIcon = false),
        height = 30.dp,
        padding = _searchResultActionPadding(context, wideHorizontal = 10.dp),
        tone = if (candidate.actionable) ButtonTone.primary else ButtonTone.neutral,
        loading = candidate.busy,
        onPressed = if (candidate.actionEnabled) ({ onPressed(room) }) else null,
    ) {
        Text(_publicRoomSearchActionLabel(candidate))
    }
}

private fun _searchResultActionPadding(context: BuildContext, wideHorizontal: Dp): EdgeInsets =
    EdgeInsets.symmetric(horizontal = if (HomeAdaptiveLayout.usesCompactLayout(context)) 4.dp else wideHorizontal)

private fun _searchResultActionWidth(context: BuildContext, hasIcon: Boolean): Dp? {
    if (!HomeAdaptiveLayout.usesCompactLayout(context)) return null
    return if (hasIcon) 90.dp else 72.dp
}

@Composable
private fun _MessageSearchResultTile(
    result: MessageSearchResult,
    title: String,
    subtitle: String,
    query: String,
    onPressed: () -> Unit,
) {
    val context = rememberBuildContext()
    _SearchResultTile(
        modifier = Modifier.testTag(searchMessageResultTag(result.message.id)),
        onPressed = onPressed,
        leading = {
            Avatar(
                label = result.room.name,
                imageUrl = AppConfigScope.of(context).resolveAssetUrl(result.room.avatarUrl),
                defaultAvatarKey = result.room.defaultAvatarKey,
                size = 30.dp,
            )
        },
        title = title,
        subtitle = subtitle,
        query = query,
        trailing = {
            Text(
                globalSearchResultTimeLabel(result),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UiTypography.label.copy(color = UiColors.textMuted),
            )
        },
    )
}

@Composable
private fun _SearchResultTile(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    query: String,
    modifier: Modifier = Modifier,
    titleTrailing: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onPressed: (() -> Unit)? = null,
) {
    val context = rememberBuildContext()
    val interactive = onPressed != null
    val compact = HomeAdaptiveLayout.usesCompactLayout(context)
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background by animateColorAsState(
        if (hovered && interactive) UiColors.surface else Color.Transparent,
        tween(90),
        label = "searchResultTileBackground",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .hoverable(interactionSource)
            .then(
                if (onPressed != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onPressed)
                } else {
                    Modifier
                },
            )
            .decoration(background, RoundedCornerShape(UiRadii.sm))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HighlightedText(
                    text = title,
                    query = query,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = UiTypography.body.copyWith(fontSize = 13.0, fontWeight = FontWeight.W600),
                )
                if (titleTrailing != null) {
                    Spacer(Modifier.width(8.dp))
                    titleTrailing()
                }
            }
            if (subtitle.trim().isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HighlightedText(
                        text = subtitle,
                        query = query,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = UiTypography.label.copyWith(color = UiColors.textMuted, height = if (compact) 1.35 else 1.2),
                    )
                }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.widthIn(max = 126.dp)) { trailing() }
        }
    }
}

@Composable
private fun _SearchPanelState(icon: ImageVector, title: String, detail: String) {
    Box(Modifier.fillMaxWidth().height(118.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, color = UiColors.textMuted, size = 22.dp)
            Spacer(Modifier.height(8.dp))
            Text(title, style = UiTypography.body)
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UiTypography.label.copy(color = UiColors.textMuted),
            )
        }
    }
}

private fun _publicRoomSearchMeta(room: PublicRoom): String = "${room.memberCount} 名成员"

private fun _publicRoomSearchActionLabel(candidate: PublicRoomJoinCandidate): String {
    val room = candidate.room
    if (room.joined) return "进入房间"
    if (candidate.pending) return "待审批"
    if (room.joinPolicy == "closed") return "不可加入"
    return "加入房间"
}

private fun _publicRoomFromRoomCard(room: RoomCard): PublicRoom =
    PublicRoom(
        id = room.id,
        rid = room.rid,
        name = room.displayName,
        avatarLabel = roomCardAvatarLabel(room),
        avatarUrl = room.avatarUrl,
        defaultAvatarKey = room.defaultAvatarKey,
        visibility = room.visibility,
        joinPolicy = if (room.visibility == "public") "open" else "closed",
        description = room.description,
        memberCount = room.memberCount,
        onlineMemberCount = room.onlineMemberCount,
        liveParticipantCount = room.liveParticipantCount,
        joined = true,
        joinState = "joined",
        myMembership = RoomMembership(joinedAt = DateTime.fromMillisecondsSinceEpoch(0, isUtc = true), role = "member"),
    )
