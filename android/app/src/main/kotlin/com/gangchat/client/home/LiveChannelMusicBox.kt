package com.gangchat.client.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import com.gangchat.client.app.MusicBoxController
import com.gangchat.client.app.MusicBoxSection
import com.gangchat.client.app.MusicBoxTransportAction
import com.gangchat.client.app.audioVolumePercentText
import com.gangchat.client.app.musicBoxActiveSourceLabel
import com.gangchat.client.app.musicBoxFormatDuration
import com.gangchat.client.app.musicBoxHasActivePlayback
import com.gangchat.client.app.musicBoxPlaylistKey
import com.gangchat.client.app.musicBoxPrimaryTransport
import com.gangchat.client.app.musicBoxProgress
import com.gangchat.client.app.musicBoxRecordSpinning
import com.gangchat.client.app.musicBoxSectionForSource
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.MusicBoxActiveSourceType
import com.gangchat.client.protocol.MusicBoxPlayback
import com.gangchat.client.protocol.MusicBoxPlaybackMode
import com.gangchat.client.protocol.MusicBoxPlaybackState
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxRequester
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DotGridBackground
import com.gangchat.client.ui.EqualizerBars
import com.gangchat.client.ui.OverflowMarqueeText
import com.gangchat.client.ui.PressableSurface
import com.gangchat.client.ui.SignalTag
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiContextMenuItem
import com.gangchat.client.ui.UiContextMenuSection
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiShadows
import com.gangchat.client.ui.UiSlider
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.showUiContextMenu
import com.gangchat.client.widgets.BoxFit
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FittedBox
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.RenderBoxHandle
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.renderBox
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Minimum height retained by the docked console. When the live controls need
 * an extra row, the panel extends upward instead of making the whole console
 * scroll.
 */
internal const val _musicBoxMinComfortableHeight: Double = 400.0

/**
 * Height of the dense controls that sit above the lists: the search field,
 * the source picker beside it, and the tab bar.
 */
internal const val _musicBoxControlHeight: Double = 30.0

/**
 * The in-pane music box console. Three fixed regions, none of which morph:
 * a now-playing strip (what is playing, from which source), a persistent
 * search-to-request bar, and a tab bar for 点歌队列 / 房间歌单 / 我的歌单.
 * Audio is delivered separately via the LiveKit session; this is purely the
 * control surface and status display.
 *
 * [volume] is the local listening volume for the music box (0–1), restored
 * from the store.
 */
@Composable
fun LiveMusicBoxPanel(
    state: MusicBoxState,
    searchController: TextEditingController,
    searchResults: List<MusicBoxSearchResult>,
    searching: Boolean,
    searchError: String?,
    source: String,
    onTogglePlayback: () -> Unit,
    onSkip: () -> Unit,
    onQueueResult: (MusicBoxSearchResult) -> Unit,
    onRemoveItem: (MusicBoxQueueItem) -> Unit,
    onSourceChanged: (String) -> Unit,
    onClose: () -> Unit,
    volume: Double,
    onVolumeChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: (() -> Unit)? = null,
    onModeChanged: ((MusicBoxPlaybackMode) -> Unit)? = null,
    controller: MusicBoxController? = null,
    roomId: String? = null,
    playlistsRevision: Int = 0,
    room: PublicRoom? = null,
    onStateChanged: ((MusicBoxState) -> Unit)? = null,
    currentUser: CurrentUser? = null,
    onResolveUserProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterCommonRoom: ((PublicRoom) -> Unit)? = null,
    userProfileActionBuilder: UserProfileActionBuilder? = null,
    onCreateFirstRoomPlaylist: (() -> Unit)? = null,
    onCreateFirstPersonalPlaylist: (() -> Unit)? = null,
    onEditRoomPlaylist: MusicPlaylistEditCallback? = null,
    onEditPersonalPlaylist: MusicPlaylistEditCallback? = null,
) = StatefulWidget(
    LiveMusicBoxPanelWidget(
        state = state,
        searchController = searchController,
        searchResults = searchResults,
        searching = searching,
        searchError = searchError,
        source = source,
        onTogglePlayback = onTogglePlayback,
        onSkip = onSkip,
        onPrevious = onPrevious,
        onModeChanged = onModeChanged,
        controller = controller,
        roomId = roomId,
        playlistsRevision = playlistsRevision,
        room = room,
        onStateChanged = onStateChanged,
        currentUser = currentUser,
        onResolveUserProfile = onResolveUserProfile,
        onResolveRoomProfile = onResolveRoomProfile,
        onEnterCommonRoom = onEnterCommonRoom,
        userProfileActionBuilder = userProfileActionBuilder,
        onCreateFirstRoomPlaylist = onCreateFirstRoomPlaylist,
        onCreateFirstPersonalPlaylist = onCreateFirstPersonalPlaylist,
        onEditRoomPlaylist = onEditRoomPlaylist,
        onEditPersonalPlaylist = onEditPersonalPlaylist,
        onQueueResult = onQueueResult,
        onRemoveItem = onRemoveItem,
        onSourceChanged = onSourceChanged,
        onClose = onClose,
        volume = volume,
        onVolumeChanged = onVolumeChanged,
        modifier = modifier,
    ),
) { _LiveMusicBoxPanelState() }

class LiveMusicBoxPanelWidget(
    val state: MusicBoxState,
    val searchController: TextEditingController,
    val searchResults: List<MusicBoxSearchResult>,
    val searching: Boolean,
    val searchError: String?,
    val source: String,
    val onTogglePlayback: () -> Unit,
    val onSkip: () -> Unit,
    val onPrevious: (() -> Unit)?,
    val onModeChanged: ((MusicBoxPlaybackMode) -> Unit)?,
    val controller: MusicBoxController?,
    val roomId: String?,
    val playlistsRevision: Int,
    val room: PublicRoom?,
    val onStateChanged: ((MusicBoxState) -> Unit)?,
    val currentUser: CurrentUser?,
    val onResolveUserProfile: UserProfileResolver?,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    val userProfileActionBuilder: UserProfileActionBuilder?,
    val onCreateFirstRoomPlaylist: (() -> Unit)?,
    val onCreateFirstPersonalPlaylist: (() -> Unit)?,
    val onEditRoomPlaylist: MusicPlaylistEditCallback?,
    val onEditPersonalPlaylist: MusicPlaylistEditCallback?,
    val onQueueResult: (MusicBoxSearchResult) -> Unit,
    val onRemoveItem: (MusicBoxQueueItem) -> Unit,
    val onSourceChanged: (String) -> Unit,
    val onClose: () -> Unit,
    val volume: Double,
    val onVolumeChanged: (Double) -> Unit,
    val modifier: Modifier,
)

/**
 * The body is one flat list, so navigation is just what is unfolded: the
 * search panel under the queue header, which playlist groups are open, and
 * which single playlist shows its tracks. Realtime snapshots never touch any
 * of these — the user decides what is open; the server decides what plays.
 */
private class _LiveMusicBoxPanelState : ViewState<LiveMusicBoxPanelWidget>() {
    private var _searchOpen = false
    private val _openSections = LinkedHashSet<MusicBoxSection>()
    private var _openPlaylistKey: String? = null

    // Incremented when the header chip asks the body to scroll to the active
    // source; the body reacts to the change, not the value.
    private var _revealSerial = 0
    private var _revealSection: MusicBoxSection? = null

    override fun didUpdateWidget(oldWidget: LiveMusicBoxPanelWidget) {
        if (oldWidget.roomId != widget.roomId || oldWidget.controller !== widget.controller) {
            _searchOpen = false
            _openSections.clear()
            _openPlaylistKey = null
        }
    }

    private fun _toggleSearch() {
        setState {
            _searchOpen = !_searchOpen
            if (!_searchOpen) widget.searchController.clear()
        }
    }

    private fun _toggleSection(section: MusicBoxSection) {
        setState {
            if (!_openSections.add(section)) _openSections.remove(section)
        }
    }

    private fun _togglePlaylist(type: MusicBoxActiveSourceType, id: String) {
        val key = musicBoxPlaylistKey(type, id)
        setState { _openPlaylistKey = if (_openPlaylistKey == key) null else key }
    }

    /** Unfold a specific playlist (from a playlist card's 查看歌单). */
    private fun _openPlaylist(type: MusicBoxActiveSourceType, id: String) {
        setState {
            _openSections.add(musicBoxSectionForSource(type))
            _openPlaylistKey = musicBoxPlaylistKey(type, id)
        }
    }

    /** Header chip: unfold whatever owns the active source and scroll to it. */
    private fun _showActiveSource() {
        val active = widget.state.activeSource
        val section = musicBoxSectionForSource(active.type)
        setState {
            if (section != MusicBoxSection.queue) {
                _openSections.add(section)
                _openPlaylistKey = musicBoxPlaylistKey(active.type, active.id)
            }
            _revealSection = section
            _revealSerial += 1
        }
    }

    @Composable
    override fun build() {
        val state = widget.state
        val shape = RoundedCornerShape(UiRadii.xl)
        val wellShape = RoundedCornerShape(UiRadii.lg)
        Column(
            widget.modifier
                .fillMaxSize()
                .boxShadow(UiShadows.popover, shape)
                .decoration(UiColors.surfaceLow, shape, UiColors.borderStrong)
                .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 8.dp),
        ) {
            _MusicBoxHeader(state = state, onOpenSource = ::_showActiveSource, onClose = widget.onClose)
            Spacer(Modifier.height(8.dp))
            _MusicBoxNowPlaying(
                state = state,
                onTogglePlayback = widget.onTogglePlayback,
                onSkip = widget.onSkip,
                onPrevious = widget.onPrevious,
                onModeChanged = widget.onModeChanged,
                currentUser = widget.currentUser,
                onResolveUserProfile = widget.onResolveUserProfile,
                onResolveRoomProfile = widget.onResolveRoomProfile,
                onEnterCommonRoom = widget.onEnterCommonRoom,
                userProfileActionBuilder = widget.userProfileActionBuilder,
            )
            Spacer(Modifier.height(8.dp))
            _MusicBoxVolume(initialVolume = widget.volume, onChanged = widget.onVolumeChanged)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .decoration(UiColors.background, wellShape, UiColors.border)
                    .clip(RoundedCornerShape(UiRadii.lg - 1.dp))
                    .padding(start = 6.dp, top = 6.dp, end = 6.dp),
                propagateMinConstraints = true,
            ) {
                _MusicBoxBody(
                    state = state,
                    searchOpen = _searchOpen,
                    openSections = _openSections.toSet(),
                    openPlaylistKey = _openPlaylistKey,
                    revealSerial = _revealSerial,
                    revealSection = _revealSection,
                    onToggleSearch = ::_toggleSearch,
                    onToggleSection = ::_toggleSection,
                    onTogglePlaylist = ::_togglePlaylist,
                    onOpenPlaylist = ::_openPlaylist,
                    searchController = widget.searchController,
                    searchResults = widget.searchResults,
                    searching = widget.searching,
                    searchError = widget.searchError,
                    source = widget.source,
                    onQueueResult = widget.onQueueResult,
                    onRemoveItem = widget.onRemoveItem,
                    onSourceChanged = widget.onSourceChanged,
                    controller = widget.controller,
                    roomId = widget.roomId,
                    playlistsRevision = widget.playlistsRevision,
                    room = widget.room,
                    onStateChanged = widget.onStateChanged,
                    currentUser = widget.currentUser,
                    onResolveUserProfile = widget.onResolveUserProfile,
                    onResolveRoomProfile = widget.onResolveRoomProfile,
                    onEnterCommonRoom = widget.onEnterCommonRoom,
                    userProfileActionBuilder = widget.userProfileActionBuilder,
                    onCreateFirstRoomPlaylist = widget.onCreateFirstRoomPlaylist,
                    onCreateFirstPersonalPlaylist = widget.onCreateFirstPersonalPlaylist,
                    onEditRoomPlaylist = widget.onEditRoomPlaylist,
                    onEditPersonalPlaylist = widget.onEditPersonalPlaylist,
                )
            }
        }
    }
}

/**
 * Narrowest header slot in which the active-source chip still shows its
 * label: the chip's fixed parts (border, padding, source mark, arrow) take
 * 53px, leaving room for at least a couple of characters.
 */
private const val _musicBoxSourceChipMinLabelWidth = 80.0

/**
 * Title row: the dot-matrix wordmark, the active-source chip (jumps to the
 * list that owns what is playing) and the close key.
 */
@Composable
private fun _MusicBoxHeader(state: MusicBoxState, onOpenSource: () -> Unit, onClose: () -> Unit) {
    val active = state.activeSource
    val playing = musicBoxHasActivePlayback(state)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(4.dp))
        Text(
            "MUSIC BOX",
            modifier = Modifier.clearAndSetSemantics { contentDescription = "音乐盒" },
            style = UiTypography.dot.copyWith(fontSize = 15.0, color = UiColors.text, letterSpacing = 0.5),
        )
        Spacer(Modifier.width(10.dp))
        BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // The docked deck can be as narrow as the live room's inner
            // width on a 320px phone; there the chip keeps only its source
            // mark (the tooltip still names it) instead of overflowing.
            val showLabel = maxWidth.value >= _musicBoxSourceChipMinLabelWidth
            PressableSurface(
                modifier = Modifier.testTag("music-box-active-source-chip"),
                height = 26.dp,
                tooltip = if (playing) "正在播放的来源" else "当前来源",
                onPressed = onOpenSource,
                padding = EdgeInsets.symmetric(horizontal = 9.dp),
                backgroundColor = if (playing) UiColors.selected else UiColors.surface,
                selected = false,
                borderColor = if (playing) UiColors.accentBorder else UiColors.border,
                borderRadius = UiRadii.pill,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playing) {
                        EqualizerBars(height = 10.dp, barWidth = 2.dp, gap = 1.5.dp)
                    } else {
                        Icon(_musicBoxSourceTypeIcon(active.type), size = 13.dp, color = UiColors.textSecondary)
                    }
                    if (showLabel) {
                        Spacer(Modifier.width(6.dp))
                        // Flexible so a long source name fades inside the
                        // chip instead of overflowing a narrow deck header.
                        _FadeOverflowText(
                            musicBoxActiveSourceLabel(active),
                            modifier = Modifier.weight(1f, fill = false).widthIn(max = 120.dp),
                            style =
                                UiTypography.label.copyWith(
                                    color = if (playing) UiColors.accent else UiColors.text,
                                    fontSize = 11.0,
                                    fontWeight = FontWeight.W700,
                                ),
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Rounded.ArrowOutward, size = 12.dp, color = if (playing) UiColors.accent else UiColors.textMuted)
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        ButtonIcon(
            modifier = Modifier.testTag("music-box-close"),
            icon = { Icon(Icons.Rounded.Close) },
            tooltip = "关闭音乐盒",
            onPressed = onClose,
            ghost = true,
            size = 28.dp,
        )
    }
}

private fun _musicBoxSourceTypeIcon(type: MusicBoxActiveSourceType): ImageVector =
    when (type) {
        MusicBoxActiveSourceType.temporary -> Icons.AutoMirrored.Filled.QueueMusic
        MusicBoxActiveSourceType.roomPlaylist -> Icons.Filled.MeetingRoom
        MusicBoxActiveSourceType.userPlaylist -> Icons.Filled.Person
    }

private const val _recordSize = 104.0

/**
 * The deck: a black dot-matrix display (status, title, artist and requester,
 * dotted progress) with the record spinning half out of its right edge, then
 * a row of hardware-style transport keys. Members the server does not let
 * control playback see a single locked line instead of disabled keys.
 */
@Composable
private fun _MusicBoxNowPlaying(
    state: MusicBoxState,
    onTogglePlayback: () -> Unit,
    onSkip: () -> Unit,
    onPrevious: (() -> Unit)?,
    onModeChanged: ((MusicBoxPlaybackMode) -> Unit)?,
    currentUser: CurrentUser?,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    userProfileActionBuilder: UserProfileActionBuilder?,
) {
    val context = com.gangchat.client.widgets.rememberBuildContext()
    val current = state.currentItem
    val spinning = musicBoxRecordSpinning(state)
    val transport = musicBoxPrimaryTransport(state)
    val hasQueue = state.queue.isNotEmpty()
    val canControl = state.playback.capabilities.canControl
    val requester = if (state.activeSource.type == MusicBoxActiveSourceType.temporary) current?.requestedBy else null
    val artist = current?.artist?.trim() ?: ""
    val statusLabel =
        if (current == null) {
            "IDLE"
        } else {
            when (state.playback.state) {
                MusicBoxPlaybackState.playing -> "NOW PLAYING"
                MusicBoxPlaybackState.paused -> "PAUSED"
                MusicBoxPlaybackState.stopped -> "STOPPED"
            }
        }
    val live = current != null && state.playback.state == MusicBoxPlaybackState.playing
    val displayShape = RoundedCornerShape(UiRadii.lg)

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .decoration(UiColors.surfacePressed, displayShape, UiColors.border)
                .clip(RoundedCornerShape(UiRadii.lg - 1.dp)),
        ) {
            DotGridBackground(
                modifier = Modifier.matchParentSize(),
                color = Color.Transparent,
                dotColor = Color(0x12FFFFFF),
                spacing = 6.dp,
                dotRadius = 0.6.dp,
            )
            if (live) {
                Canvas(Modifier.matchParentSize()) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(UiColors.accent.copy(alpha = 0.14f), UiColors.accent.copy(alpha = 0f)),
                            center = Offset(size.width, size.height / 2),
                            radius = 1.1f * minOf(size.width, size.height),
                        ),
                    )
                }
            }
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                _VinylRecord(
                    modifier = Modifier.offset(x = (_recordSize * 0.42).dp),
                    spinning = spinning,
                    label = current?.title,
                    size = _recordSize,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 10.dp, end = (_recordSize * 0.58 + 10).dp, bottom = 10.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    SignalTag(
                        modifier = Modifier.weight(1f, fill = false),
                        label = statusLabel,
                        color = if (live) UiColors.accent else UiColors.textMuted,
                        dot = true,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OverflowMarqueeText(
                    modifier = Modifier.fillMaxWidth().testTag("music-box-now-playing:title"),
                    trackKey = "music-box-now-playing:title-marquee-track",
                    text = current?.title ?: "未在播放",
                    style =
                        UiTypography.title.copyWith(
                            color = if (current == null) UiColors.textMuted else UiColors.text,
                            fontSize = 16.0,
                            height = 1.25,
                        ),
                )
                Spacer(Modifier.height(3.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    _FadeOverflowText(
                        when {
                            current == null -> "在下方搜索歌曲点歌"
                            artist.isEmpty() -> "未知艺人"
                            else -> artist
                        },
                        modifier = Modifier.weight(1f, fill = false),
                        style = UiTypography.mono.copyWith(color = UiColors.textSecondary, fontSize = 11.0),
                    )
                    if (requester != null) {
                        Spacer(Modifier.width(8.dp))
                        _MusicBoxRequesterChip(
                            modifier = Modifier.testTag("music-box-now-playing:requester"),
                            requester = requester,
                            currentUser = currentUser,
                            onResolveUserProfile = onResolveUserProfile,
                            onResolveRoomProfile = onResolveRoomProfile,
                            onEnterCommonRoom = onEnterCommonRoom,
                            userProfileActionBuilder = userProfileActionBuilder,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                _MusicBoxProgressBar(state = state)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!canControl) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .decoration(shape = RoundedCornerShape(UiRadii.md), borderColor = UiColors.border),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, size = 14.dp, color = UiColors.textMuted)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "播放由房间管理员控制",
                        modifier = Modifier.testTag("music-box-transport-locked"),
                        style = UiTypography.label.copyWith(color = UiColors.textMuted, fontWeight = FontWeight.W600),
                    )
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 6.0
                val unit = ((maxWidth.value - gap * 3) / 5).coerceIn(30.0, 120.0)
                val modeAnchor = androidx.compose.runtime.remember { RenderBoxHandle() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ButtonIcon(
                        modifier = Modifier.testTag("music-box-transport-mode").renderBox(modeAnchor),
                        icon = { Icon(_musicBoxModeIcon(state.playback.mode)) },
                        tooltip = "播放模式：${_musicBoxModeLabel(state.playback.mode)}",
                        selected = state.playback.mode != MusicBoxPlaybackMode.sequential,
                        onPressed =
                            if (state.playback.capabilities.canChangeMode && onModeChanged != null) {
                                { unawaited { _showMusicBoxModeMenu(context, modeAnchor, state.playback, onModeChanged) } }
                            } else {
                                null
                            },
                        size = 40.dp,
                        width = unit.dp,
                        radius = UiRadii.md,
                        iconSize = 18.dp,
                    )
                    Spacer(Modifier.width(gap.dp))
                    ButtonIcon(
                        modifier = Modifier.testTag("music-box-transport-previous"),
                        icon = { Icon(Icons.Rounded.SkipPrevious) },
                        tooltip = "上一首",
                        onPressed = if (state.playback.canPrevious) onPrevious else null,
                        size = 40.dp,
                        width = unit.dp,
                        radius = UiRadii.md,
                        iconSize = 20.dp,
                    )
                    Spacer(Modifier.width(gap.dp))
                    ButtonIcon(
                        modifier = Modifier.testTag("music-box-primary-playback"),
                        icon = { Icon(if (transport == MusicBoxTransportAction.pause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow) },
                        tooltip =
                            when (transport) {
                                MusicBoxTransportAction.pause -> "暂停"
                                MusicBoxTransportAction.resume -> "继续播放"
                                MusicBoxTransportAction.play -> "播放"
                            },
                        tone = ButtonTone.primary,
                        onPressed = if (hasQueue) onTogglePlayback else null,
                        size = 40.dp,
                        width = (unit * 2).dp,
                        radius = UiRadii.md,
                        iconSize = 24.dp,
                    )
                    Spacer(Modifier.width(gap.dp))
                    ButtonIcon(
                        modifier = Modifier.testTag("music-box-transport-next"),
                        icon = { Icon(Icons.Rounded.SkipNext) },
                        tooltip = "下一首",
                        onPressed = if (hasQueue && state.playback.canNext) onSkip else null,
                        size = 40.dp,
                        width = unit.dp,
                        radius = UiRadii.md,
                        iconSize = 20.dp,
                    )
                }
            }
        }
    }
}

/**
 * Avatar + name for the person who requested the current song. The avatar
 * carries the user card; the name is spelled out, so no tooltip is needed.
 */
@Composable
private fun _MusicBoxRequesterChip(
    requester: MusicBoxRequester,
    currentUser: CurrentUser?,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    userProfileActionBuilder: UserProfileActionBuilder?,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        _MusicBoxRequesterAvatar(
            requester = requester,
            currentUser = currentUser,
            onResolveUserProfile = onResolveUserProfile,
            onResolveRoomProfile = onResolveRoomProfile,
            onEnterCommonRoom = onEnterCommonRoom,
            userProfileActionBuilder = userProfileActionBuilder,
            size = 16.0,
        )
        Spacer(Modifier.width(5.dp))
        _FadeOverflowText(
            requester.displayName,
            modifier = Modifier.widthIn(max = 72.dp),
            style = UiTypography.label.copyWith(color = UiColors.textMuted, fontSize = 10.5, fontWeight = FontWeight.W600),
        )
    }
}

private fun _musicBoxModeIcon(mode: MusicBoxPlaybackMode): ImageVector =
    when (mode) {
        MusicBoxPlaybackMode.sequential -> Icons.Filled.FormatListNumbered
        MusicBoxPlaybackMode.repeatOne -> Icons.Filled.RepeatOne
        MusicBoxPlaybackMode.repeatAll -> Icons.Filled.Repeat
        MusicBoxPlaybackMode.shuffle -> Icons.Filled.Shuffle
    }

private fun _musicBoxModeLabel(mode: MusicBoxPlaybackMode): String =
    when (mode) {
        MusicBoxPlaybackMode.sequential -> "顺序播放"
        MusicBoxPlaybackMode.repeatOne -> "单曲循环"
        MusicBoxPlaybackMode.repeatAll -> "列表循环"
        MusicBoxPlaybackMode.shuffle -> "随机播放"
    }

private suspend fun _showMusicBoxModeMenu(
    context: BuildContext,
    anchor: RenderBoxHandle,
    playback: MusicBoxPlayback,
    onChanged: (MusicBoxPlaybackMode) -> Unit,
) {
    val position =
        if (!anchor.hasSize) {
            DpOffset.Zero
        } else {
            anchor.localToGlobal(DpOffset(anchor.size.width, anchor.size.height))
        }
    showUiContextMenu(
        context,
        position = position,
        sections =
            listOf(
                UiContextMenuSection(
                    playback.capabilities.allowedModes.map { mode ->
                        UiContextMenuItem(
                            label = _musicBoxModeLabel(mode),
                            icon = _musicBoxModeIcon(mode),
                            selected = mode == playback.mode,
                            onPressed = { onChanged(mode) },
                        )
                    },
                ),
            ),
    )
}

/**
 * A flat volume strip: mute key, an inline [UiSlider], and the level in
 * dot-matrix digits. Drives the local listening volume of the music box bot's
 * audio track via [onChanged] — purely a per-listener preference, independent
 * of the room's output volume. [initialVolume] seeds it from the restored
 * store; the widget then owns the value while mounted.
 */
@Composable
private fun _MusicBoxVolume(initialVolume: Double, onChanged: (Double) -> Unit) =
    StatefulWidget(_MusicBoxVolumeWidget(initialVolume, onChanged)) { _MusicBoxVolumeState() }

private class _MusicBoxVolumeWidget(val initialVolume: Double, val onChanged: (Double) -> Unit)

private class _MusicBoxVolumeState : ViewState<_MusicBoxVolumeWidget>() {
    private var _volume = 0.0

    // The level to restore to when unmuting; captured at the moment of muting.
    private var _premute = 0.7
    private var _hovered = false

    // True while a slider drag is in flight. The pointer gets captured by the
    // slider for the whole drag, so the cursor can wander off the pill without
    // meaning to collapse it — keep it open until the drag ends.
    private var _dragging = false

    private val _expanded: Boolean get() = _expandedByDefault || _hovered || _dragging

    private val _muted: Boolean get() = _volume <= 0

    override fun initState() {
        _volume = widget.initialVolume
        _premute = if (widget.initialVolume > 0) widget.initialVolume else 0.7
    }

    override fun didUpdateWidget(oldWidget: _MusicBoxVolumeWidget) {
        if (oldWidget.initialVolume == widget.initialVolume) return
        val nextVolume = widget.initialVolume.coerceIn(0.0, 1.0)
        _volume = nextVolume
        if (nextVolume > 0) _premute = nextVolume
    }

    private fun _setVolume(value: Double) {
        setState { _volume = value }
        widget.onChanged(value)
    }

    private fun _toggleMute() {
        if (_muted) {
            _setVolume(if (_premute > 0) _premute else 0.7)
        } else {
            _premute = _volume
            _setVolume(0.0)
        }
    }

    // A flat fader strip: mute key, the slider, and the level in monospace.
    @Composable
    override fun build() {
        val active = !_muted
        val foreground = if (active) UiColors.accent else UiColors.text
        val shape = RoundedCornerShape(UiRadii.md)
        Row(
            Modifier
                .fillMaxWidth()
                ._liveMouseHover(onEnter = { setState { _hovered = true } }, onExit = { setState { _hovered = false } })
                .testTag("music-box-volume-control")
                .height((_size + 8).dp)
                .decoration(UiColors.surface, shape, if (_expanded && _hovered) UiColors.borderStrong else UiColors.border),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(4.dp))
            _VolumeIconButton(icon = _volumeLevelIcon(_volume), color = foreground, size = _size + 4, onTap = ::_toggleMute)
            Text(
                "VOL",
                style =
                    UiTypography.overline.copyWith(
                        color = if (foreground == UiColors.accent) UiColors.textSecondary else UiColors.textMuted,
                        fontSize = 9.5,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            UiSlider(
                modifier = Modifier.weight(1f),
                value = _volume,
                hoverLabel = audioVolumePercentText(_volume),
                onChangeStart = { setState { _dragging = true } },
                onChangeEnd = { setState { _dragging = false } },
                onChanged = ::_setVolume,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                (_volume * 100).roundToInt().toString().padStart(2, '0'),
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.End,
                style = UiTypography.dot.copyWith(fontSize = 14.0, color = if (_muted) UiColors.textMuted else UiColors.accent),
            )
            Spacer(Modifier.width((_pad - 2).dp))
        }
    }

    companion object {
        // The mute key size; the strip is a little taller.
        private const val _size = 24.0
        private const val _pad = 12.0

        // The fader strip is always fully shown; hover/drag only light its frame.
        private const val _expandedByDefault = true
    }
}

/** A bare, borderless tap target for the mute icon inside the volume pill. */
@Composable
private fun _VolumeIconButton(icon: ImageVector, color: Color, size: Double, onTap: () -> Unit) {
    Box(Modifier.size(size.dp)._liveTap(onTap), contentAlignment = Alignment.Center) {
        Icon(icon, color = color, size = (size * 0.46).dp)
    }
}

/**
 * A spinning black vinyl record. Rotates continuously while [spinning], and
 * freezes its current angle when paused/stopped — an at-a-glance read of
 * whether the music box is playing.
 */
@Composable
internal fun _VinylRecord(spinning: Boolean, modifier: Modifier = Modifier, label: String? = null, size: Double = 40.0) =
    StatefulWidget(_VinylRecordWidget(spinning, label, size, modifier)) { _VinylRecordState() }

private class _VinylRecordWidget(val spinning: Boolean, val label: String?, val size: Double, val modifier: Modifier)

private class _VinylRecordState : ViewState<_VinylRecordWidget>() {
    // A ViewState without mutable fields is inferred stable by the Compose
    // compiler, so build() and the slot lambdas capturing this state would
    // skip when the parent passes a new widget. Flutter always rebuilds.
    @Suppress("unused")
    private var _rebuildWithParent = true

    // The controller's value in turns (Dart's `AnimationController`, 6 s per turn).
    private val _turns = mutableFloatStateOf(0f)

    @Composable
    override fun build() {
        LaunchedEffect(widget.spinning) {
            // Freeze at the current angle rather than snapping back to zero.
            if (!widget.spinning) return@LaunchedEffect
            val start = _turns.floatValue
            var startNanos = -1L
            while (true) {
                withInfiniteAnimationFrameNanos { now ->
                    if (startNanos < 0) startNanos = now
                    val elapsedSeconds = (now - startNanos) / 1_000_000_000.0
                    val turns = start + elapsedSeconds / 6.0
                    _turns.floatValue = (turns - floor(turns)).toFloat()
                }
            }
        }
        Box(widget.modifier.size(widget.size.dp)) {
            Canvas(Modifier.matchParentSize().graphicsLayer { rotationZ = _turns.floatValue * 360f }) { _paintVinyl() }
            // The light catching the grooves stays put while the disc turns.
            Canvas(Modifier.matchParentSize()) { _paintVinylSheen() }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope._paintVinyl() {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = size.width / 2
    val unit = 1.dp.toPx()
    drawCircle(Color(0xFF0B0B0D), radius, center)
    drawCircle(UiColors.borderStrong, radius - 0.5f * unit, center, style = Stroke(width = unit))
    var index = 0
    var r = radius - 4 * unit
    while (r > radius * 0.4f) {
        drawCircle(if (index % 2 == 0) Color(0xFF1A1A1E) else Color(0xFF131316), r, center, style = Stroke(width = 0.8f * unit))
        index += 1
        r -= 2.6f * unit
    }

    // Lime label with a printed stripe so the rotation reads at a glance.
    val labelRadius = radius * 0.36f
    drawCircle(UiColors.accent, labelRadius, center)
    val labelClip = Path().apply { addOval(Rect(center, labelRadius)) }
    clipPath(labelClip) {
        drawRect(
            UiColors.onAccent.copy(alpha = 0.85f),
            topLeft = Offset(center.x - labelRadius, center.y - labelRadius * 0.62f),
            size = Size(labelRadius * 2, labelRadius * 0.28f),
        )
    }
    drawCircle(UiColors.surfacePressed, labelRadius * 0.16f, center)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope._paintVinylSheen() {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = size.width / 2
    val ring =
        Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(center, radius - 2.dp.toPx()))
            addOval(Rect(center, radius * 0.38f))
        }
    drawPath(
        ring,
        Brush.sweepGradient(
            0.0f to Color.White.copy(alpha = 0f),
            0.1f to Color.White.copy(alpha = 0.07f),
            0.22f to Color.White.copy(alpha = 0f),
            0.5f to Color.White.copy(alpha = 0f),
            0.6f to Color.White.copy(alpha = 0.05f),
            0.72f to Color.White.copy(alpha = 0f),
            center = center,
        ),
    )
}

/**
 * Server-authoritative progress, drawn as a row of dots that light up as
 * the song plays. The server pushes a fresh snapshot every second, so the
 * bar renders the snapshot's reported position — no local stepping.
 */
@Composable
private fun _MusicBoxProgressBar(state: MusicBoxState) {
    val progress = musicBoxProgress(state)
    val hasTrack = state.currentItem != null
    Column(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .semantics { stateDescription = "${(progress.fraction * 100).roundToInt()}%" },
        ) {
            _paintDotProgress(fraction = progress.fraction, active = hasTrack)
        }
        Spacer(Modifier.height(7.dp))
        // Scales the readout down only when the display is narrower than it
        // (the docked deck on a 320px phone); otherwise it draws at full size.
        FittedBox(modifier = Modifier.fillMaxWidth(), fit = BoxFit.scaleDown, alignment = Alignment.BottomStart) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    musicBoxFormatDuration(progress.positionMs),
                    style = UiTypography.dot.copyWith(fontSize = 17.0, height = 1.0, color = if (hasTrack) UiColors.text else UiColors.textMuted),
                )
                Spacer(Modifier.width(6.dp))
                Text("/", style = UiTypography.mono.copyWith(fontSize = 10.0, color = UiColors.textMuted, height = 1.0))
                Spacer(Modifier.width(6.dp))
                Text(
                    musicBoxFormatDuration(progress.durationMs),
                    style = UiTypography.mono.copyWith(fontSize = 10.0, color = UiColors.textMuted, height = 1.0),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope._paintDotProgress(fraction: Double, active: Boolean) {
    val step = 5.dp.toPx()
    val radius = 1.3.dp.toPx()
    val count = max(2, floor(size.width / step).toInt())
    val spacing = (size.width - radius * 2) / (count - 1)
    val lit = if (active) (fraction.coerceIn(0.0, 1.0) * (count - 1)).roundToInt() else -1
    val y = size.height / 2
    val off = Color(0xFF2A2A30)
    val on = UiColors.accent
    for (i in 0 until count) {
        val x = radius + i * spacing
        if (i == lit) {
            drawCircle(UiColors.accent.copy(alpha = 0.25f), radius * 2.6f, Offset(x, y))
            drawCircle(on, radius * 1.6f, Offset(x, y))
        } else {
            drawCircle(if (i < lit) on else off, radius, Offset(x, y))
        }
    }
}
