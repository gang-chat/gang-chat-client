package com.gangchat.client.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MusicBoxController
import com.gangchat.client.app.MusicBoxSection
import com.gangchat.client.app.musicBoxActiveSourceLabel
import com.gangchat.client.app.musicBoxArtistsLabel
import com.gangchat.client.app.musicBoxControlErrorMessage
import com.gangchat.client.app.musicBoxHasActivePlayback
import com.gangchat.client.app.musicBoxPlaylistKey
import com.gangchat.client.app.musicBoxQueueStatusLabel
import com.gangchat.client.app.musicBoxRequestQueueContainsTrack
import com.gangchat.client.app.musicBoxSectionForSource
import com.gangchat.client.app.musicBoxSourceIsActive
import com.gangchat.client.app.musicBoxSourceLabel
import com.gangchat.client.app.musicBoxSources
import com.gangchat.client.app.normalizedMusicBoxSource
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.MusicBoxActiveSourceType
import com.gangchat.client.protocol.MusicBoxPlaybackState
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxQueueItemStatus
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PersonalMusicPlaylistItem
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.musicBoxActiveSourceTypeValue
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.Segment
import com.gangchat.client.ui.SegmentedControl
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiMotion
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.ui.showFloatingSuccessNotice
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.FocusScope
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.showDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lower body of the music box: one flat scrolling list. The request queue
 * sits on top and is always open; a search button on its header unfolds a
 * search panel right under it. Below come 房间歌单 and 我的歌单 as collapsed
 * groups; opening a group lists its playlists, opening a playlist lists its
 * tracks in place. Nothing morphs and nothing navigates away.
 *
 * PORT: Dart builds the list from slivers whose section states (search focus,
 * loaded playlists, unfolded tracks) live as long as the body. A
 * `LazyColumn` disposes rows scrolled out of view, so those section states
 * are kept by the body ([_MusicBoxQueueSectionHolder],
 * [_MusicBoxPlaylistGroupHolder], [_MusicBoxPlaylistTracksHolder]) and
 * updated on every body build, as Flutter updates the section widgets.
 */
@Composable
internal fun _MusicBoxBody(
    state: MusicBoxState,
    searchOpen: Boolean,
    openSections: Set<MusicBoxSection>,
    openPlaylistKey: String?,
    revealSerial: Int,
    revealSection: MusicBoxSection?,
    onToggleSearch: () -> Unit,
    onToggleSection: (MusicBoxSection) -> Unit,
    onTogglePlaylist: (MusicBoxActiveSourceType, String) -> Unit,
    onOpenPlaylist: (MusicBoxActiveSourceType, String) -> Unit,
    searchController: TextEditingController,
    searchResults: List<MusicBoxSearchResult>,
    searching: Boolean,
    searchError: String?,
    source: String,
    onQueueResult: (MusicBoxSearchResult) -> Unit,
    onRemoveItem: (MusicBoxQueueItem) -> Unit,
    onSourceChanged: (String) -> Unit,
    controller: MusicBoxController?,
    roomId: String?,
    playlistsRevision: Int,
    room: PublicRoom?,
    onStateChanged: ((MusicBoxState) -> Unit)?,
    currentUser: CurrentUser?,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    userProfileActionBuilder: UserProfileActionBuilder?,
    onCreateFirstRoomPlaylist: (() -> Unit)?,
    onCreateFirstPersonalPlaylist: (() -> Unit)?,
    onEditRoomPlaylist: MusicPlaylistEditCallback?,
    onEditPersonalPlaylist: MusicPlaylistEditCallback?,
) = StatefulWidget(
    _MusicBoxBodyWidget(
        state, searchOpen, openSections, openPlaylistKey, revealSerial, revealSection, onToggleSearch, onToggleSection, onTogglePlaylist,
        onOpenPlaylist, searchController, searchResults, searching, searchError, source, onQueueResult, onRemoveItem, onSourceChanged,
        controller, roomId, playlistsRevision, room, onStateChanged, currentUser, onResolveUserProfile, onResolveRoomProfile,
        onEnterCommonRoom, userProfileActionBuilder, onCreateFirstRoomPlaylist, onCreateFirstPersonalPlaylist, onEditRoomPlaylist,
        onEditPersonalPlaylist,
    ),
) { _MusicBoxBodyState() }

internal class _MusicBoxBodyWidget(
    val state: MusicBoxState,
    val searchOpen: Boolean,
    val openSections: Set<MusicBoxSection>,
    val openPlaylistKey: String?,
    val revealSerial: Int,
    val revealSection: MusicBoxSection?,
    val onToggleSearch: () -> Unit,
    val onToggleSection: (MusicBoxSection) -> Unit,
    val onTogglePlaylist: (MusicBoxActiveSourceType, String) -> Unit,
    val onOpenPlaylist: (MusicBoxActiveSourceType, String) -> Unit,
    val searchController: TextEditingController,
    val searchResults: List<MusicBoxSearchResult>,
    val searching: Boolean,
    val searchError: String?,
    val source: String,
    val onQueueResult: (MusicBoxSearchResult) -> Unit,
    val onRemoveItem: (MusicBoxQueueItem) -> Unit,
    val onSourceChanged: (String) -> Unit,
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
)

/** One row of the flat list (Dart's sliver children). */
internal class _MusicBoxListItem(val key: Any, val content: @Composable () -> Unit)

/** Builds unique lazy-list keys even when the server repeats an id. */
internal class _MusicBoxListKeys {
    private val _seen = HashMap<String, Int>()

    fun key(base: String): String {
        val count = _seen[base] ?: 0
        _seen[base] = count + 1
        return if (count == 0) base else "$base#$count"
    }
}

internal class _MusicBoxBodyState : ViewState<_MusicBoxBodyWidget>() {
    val listState = LazyListState()
    private val _searchListener: () -> Unit = { if (mounted) setState() }
    private lateinit var _queue: _MusicBoxQueueSectionHolder
    private val _groups = LinkedHashMap<MusicBoxActiveSourceType, _MusicBoxPlaylistGroupHolder>()
    private var _inBuild = false
    private var _scope: CoroutineScope? = null
    private var _pendingSectionReveal: MusicBoxSection? = null
    internal var pendingCurrentRevealIndex: Int? = null
    private val _sectionHeaderIndex = HashMap<MusicBoxSection, Int>()

    override fun initState() {
        widget.searchController.addListener(_searchListener)
        _queue = _MusicBoxQueueSectionHolder(this)
        _queue.init()
    }

    override fun didUpdateWidget(oldWidget: _MusicBoxBodyWidget) {
        if (oldWidget.searchController !== widget.searchController) {
            oldWidget.searchController.removeListener(_searchListener)
            widget.searchController.addListener(_searchListener)
        }
        if (oldWidget.revealSerial != widget.revealSerial) {
            val section = widget.revealSection
            if (section != null) _pendingSectionReveal = section
        }
    }

    override fun dispose() {
        widget.searchController.removeListener(_searchListener)
        for (group in _groups.values) group.dispose()
        _groups.clear()
        _queue.dispose()
    }

    /** A section's setState: inside the body's build the change is already being built. */
    fun sectionSetState(fn: () -> Unit) {
        if (_inBuild) {
            fn()
        } else {
            setState(fn)
        }
    }

    private suspend fun _viewPlaylist(playlist: PersonalMusicPlaylist, roomScoped: Boolean) {
        FocusScope.unfocus(context)
        widget.onOpenPlaylist(
            if (roomScoped) MusicBoxActiveSourceType.roomPlaylist else MusicBoxActiveSourceType.userPlaylist,
            playlist.id,
        )
    }

    private fun _syncGroups(hasQuery: Boolean) {
        // While searching, the results take the whole list so the user is
        // not scrolling past playlists to find the next hit (Dart removes the
        // group slivers, disposing their state).
        if (hasQuery) {
            for (group in _groups.values) group.dispose()
            _groups.clear()
            return
        }
        for (type in listOf(MusicBoxActiveSourceType.roomPlaylist, MusicBoxActiveSourceType.userPlaylist)) {
            val existing = _groups[type]
            if (existing == null) {
                val group = _MusicBoxPlaylistGroupHolder(this, type)
                _groups[type] = group
                group.init()
            } else {
                existing.update()
            }
        }
    }

    fun recordSectionHeader(section: MusicBoxSection, index: Int) {
        _sectionHeaderIndex[section] = index
    }

    private suspend fun _awaitListLayout() {
        withFrameNanos { }
        var frames = 0
        while (listState.layoutInfo.visibleItemsInfo.isEmpty() && frames < 10) {
            withFrameNanos { }
            frames += 1
        }
    }

    private suspend fun _revealSection(section: MusicBoxSection) {
        _awaitListLayout()
        if (!mounted) return
        val index = _sectionHeaderIndex[section] ?: return
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (info != null) {
            listState.animateScrollBy(info.offset.toFloat(), tween(180, easing = Curves.easeOutCubic))
        } else {
            listState.scrollToItem(index)
        }
    }

    /**
     * Rows are built lazily, so a current item deep in the queue has no
     * layout yet: jump to it first, then center it once its real height is
     * known (Dart: estimated jump, then `Scrollable.ensureVisible`).
     */
    private suspend fun _revealCurrent(index: Int) {
        _awaitListLayout()
        if (!mounted) return
        listState.scrollToItem(index)
        withFrameNanos { }
        val layout = listState.layoutInfo
        val info = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
        val itemCenter = info.offset + info.size / 2f
        listState.scrollBy(itemCenter - viewportCenter)
    }

    @Composable
    override fun build() {
        _inBuild = true
        _scope = rememberCoroutineScope()
        val query = widget.searchController.text
        val hasQuery = widget.searchOpen && query.trim().isNotEmpty()
        _queue.update()
        _syncGroups(hasQuery)

        val items = ArrayList<_MusicBoxListItem>()
        val keys = _MusicBoxListKeys()
        _queue.emit(items, keys, query)
        if (!hasQuery) {
            _groups[MusicBoxActiveSourceType.roomPlaylist]?.emit(items, keys)
            _groups[MusicBoxActiveSourceType.userPlaylist]?.emit(items, keys)
        }

        MusicPlaylistCardHostScope(
            MusicPlaylistCardHostScopeData(
                currentUser = widget.currentUser,
                room = widget.room,
                onResolveUserProfile = widget.onResolveUserProfile,
                onResolveRoomProfile = widget.onResolveRoomProfile,
                onEnterCommonRoom = widget.onEnterCommonRoom,
                userProfileActionBuilder = widget.userProfileActionBuilder,
                onStateChanged = widget.onStateChanged,
                onViewPlaylist = { playlist, roomScoped -> _viewPlaylist(playlist, roomScoped) },
                onEditRoomPlaylist = widget.onEditRoomPlaylist,
                onEditPersonalPlaylist = widget.onEditPersonalPlaylist,
            ),
        ) {
            LazyColumn(Modifier.fillMaxSize().testTag("music-box-list"), state = listState) {
                for (item in items) {
                    item(key = item.key) { item.content() }
                }
            }
        }

        val scope = _scope
        SideEffect {
            _inBuild = false
            val section = _pendingSectionReveal
            if (section != null) {
                _pendingSectionReveal = null
                scope?.launch { _revealSection(section) }
            }
            val current = pendingCurrentRevealIndex
            if (current != null) {
                pendingCurrentRevealIndex = null
                scope?.launch { _revealCurrent(current) }
            }
        }
        _inBuild = false
    }
}

/** Runs a music-box write, applies the returned snapshot, and surfaces failures as a floating notice. */
internal suspend fun _musicBoxRunWrite(
    context: BuildContext,
    write: suspend () -> MusicBoxState,
    onStateChanged: ((MusicBoxState) -> Unit)?,
    failureMessage: String,
    successMessage: String? = null,
) {
    try {
        val state = write()
        onStateChanged?.invoke(state)
        if (successMessage != null && context.mounted) showFloatingSuccessNotice(context, successMessage)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (context.mounted) showFloatingErrorNotice(context, musicBoxControlErrorMessage(error, failureMessage))
    }
}

/**
 * A box-height slot for the shared empty/loading/error widgets, which center
 * themselves and therefore need bounded height inside the list.
 */
@Composable
internal fun _MusicBoxSliverBox(height: Double = 120.0, child: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(height.dp), propagateMinConstraints = true) { child() }
}

/** Tappable header of a collapsed/expanded group in the flat list. */
@Composable
private fun _MusicBoxGroupHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    open: Boolean,
    playing: Boolean,
    onToggle: () -> Unit,
    channel: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val turns by animateFloatAsState(if (open) 0.25f else 0f, tween(UiMotion.fast.inWholeMilliseconds.toInt()), label = "groupChevron")
    Row(
        modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = title
                liveExpanded = open
                if (open) collapse { onToggle(); true } else expand { onToggle(); true }
            }
            ._liveTap(onToggle)
            .padding(top = 4.dp)
            .height(40.dp)
            ._liveTopBorder(UiColors.border)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(channel, style = UiTypography.dot.copyWith(fontSize = 13.0, color = if (playing) UiColors.accent else UiColors.textMuted))
        Spacer(Modifier.width(10.dp))
        Icon(if (playing) Icons.Filled.GraphicEq else icon, size = 15.dp, color = if (playing) UiColors.accent else UiColors.textSecondary)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = UiTypography.label.copyWith(color = if (playing) UiColors.accent else UiColors.text, fontWeight = FontWeight.W700),
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text("$count", style = UiTypography.mono.copyWith(color = UiColors.textMuted, fontSize = 10.5))
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.graphicsLayer { rotationZ = turns * 360f }) {
            Icon(Icons.Rounded.ChevronRight, size = 18.dp, color = UiColors.textMuted)
        }
    }
}

/**
 * The request queue: header with the search toggle and queue-wide actions,
 * the unfolded search panel, then either the search results or the rows.
 */
internal class _MusicBoxQueueSectionHolder(private val body: _MusicBoxBodyState) {
    private val _searchFocus = FocusNode()
    private var _revealedItemId: String? = null
    private var _playingNowItemId: String? = null
    private var _lastSearchOpen = false
    private var _disposed = false

    private val widget: _MusicBoxBodyWidget get() = body.widget
    private val mounted: Boolean get() = !_disposed && body.mounted

    private val _isActive: Boolean get() = musicBoxSourceIsActive(widget.state, MusicBoxActiveSourceType.temporary)

    private val _items: List<MusicBoxQueueItem> get() = if (_isActive) widget.state.queue else widget.state.temporaryQueue

    private fun setState(fn: () -> Unit) {
        if (!mounted) return
        body.sectionSetState(fn)
    }

    fun init() {
        _lastSearchOpen = widget.searchOpen
    }

    fun update() {
        if (!_lastSearchOpen && widget.searchOpen) {
            // Dart requests focus after the panel's first frame; an
            // unattached node defers the request until the field attaches.
            _searchFocus.requestFocus()
        }
        _lastSearchOpen = widget.searchOpen
    }

    fun dispose() {
        _disposed = true
    }

    private fun _scheduleRevealCurrent(currentItemId: String, lazyIndex: Int) {
        if (currentItemId.isEmpty() || lazyIndex < 0 || _revealedItemId == currentItemId) return
        _revealedItemId = currentItemId
        body.pendingCurrentRevealIndex = lazyIndex
    }

    /**
     * Plays [item]. While the request queue is the active source this is a
     * server `play_now`; while a saved playlist is playing, picking a queue
     * song switches the room back to the request queue starting from it.
     */
    private suspend fun _playItem(item: MusicBoxQueueItem) {
        val controller = widget.controller
        val roomId = widget.roomId
        if (controller == null || roomId == null || _playingNowItemId != null) return
        val switching = !_isActive
        setState { _playingNowItemId = item.id }
        _musicBoxRunWrite(
            body.context,
            write = {
                if (switching) {
                    controller.activatePlaylist(roomId = roomId, sourceType = MusicBoxActiveSourceType.temporary, startItemId = item.id)
                } else {
                    controller.playNow(roomId = roomId, item = item)
                }
            },
            onStateChanged = widget.onStateChanged,
            failureMessage = if (switching) "播放点歌队列失败，请重试" else "优先播放失败，请重试",
            successMessage = if (switching) null else "已优先播放",
        )
        if (mounted) setState { _playingNowItemId = null }
    }

    private suspend fun _confirmAndClear() {
        val controller = widget.controller
        val roomId = widget.roomId
        if (controller == null || roomId == null) return
        val confirmed =
            showDialog<Boolean>(body.context) { dialogContext ->
                DialogFrame(
                    title = "清空点歌队列",
                    icon = Icons.Outlined.DeleteSweep,
                    adaptiveActions =
                        listOf(
                            ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(dialogContext).pop(false) }),
                            ResponsiveDialogAction(
                                buttonKey = "music-box-confirm-clear-temporary-queue",
                                label = "清空队列",
                                icon = Icons.Outlined.DeleteSweep,
                                tone = ButtonTone.danger,
                                onPressed = { Navigator.of(dialogContext).pop(true) },
                            ),
                        ),
                    child = { Text("确认清空当前点歌队列？此操作不会删除已保存的歌单。", style = UiTypography.body) },
                )
            }
        if (confirmed != true || !mounted) return
        _musicBoxRunWrite(
            body.context,
            write = { controller.clearTemporaryQueue(roomId = roomId, currentState = widget.state) },
            onStateChanged = widget.onStateChanged,
            failureMessage = "清空点歌队列失败，请重试",
            successMessage = "已清空点歌队列",
        )
    }

    private fun _headerTitle(count: Int): String {
        val status =
            if (!_isActive) {
                "未播放"
            } else {
                when (widget.state.playback.state) {
                    MusicBoxPlaybackState.playing -> "正在播放"
                    MusicBoxPlaybackState.paused -> "已暂停"
                    MusicBoxPlaybackState.stopped -> "未播放"
                }
            }
        return "点歌队列 · $count 首 · $status"
    }

    @Composable
    private fun _Header() {
        val state = widget.state
        val items = _items
        val canWrite = widget.controller != null && widget.roomId != null
        val lit = _isActive && musicBoxHasActivePlayback(state)
        Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            _MusicBoxSectionHeader(
                modifier = Modifier.testTag("music-box-queue-header"),
                channel = "01",
                channelLit = lit,
                leading = {
                    Icon(
                        if (lit) Icons.Filled.GraphicEq else Icons.AutoMirrored.Filled.QueueMusic,
                        size = 15.dp,
                        color = if (lit) UiColors.accent else UiColors.textSecondary,
                    )
                },
                title = _headerTitle(items.size),
                trailing =
                    buildList {
                        add {
                            _MusicBoxRowAction(
                                modifier = Modifier.testTag("music-box-search-toggle"),
                                icon = if (widget.searchOpen) Icons.Filled.SearchOff else Icons.Filled.Search,
                                label = if (widget.searchOpen) "收起搜索" else "搜索歌曲点歌",
                                tone = ButtonTone.primary,
                                selected = widget.searchOpen,
                                onPressed = widget.onToggleSearch,
                                size = 26.0,
                            )
                        }
                        if (items.isNotEmpty() && state.canClearTemporary) {
                            add {
                                _MusicBoxRowAction(
                                    modifier = Modifier.testTag("music-box-queue-clear"),
                                    icon = Icons.Outlined.DeleteSweep,
                                    label = "清空点歌队列",
                                    tone = ButtonTone.danger,
                                    onPressed = if (canWrite) ({ unawaited { _confirmAndClear() } }) else null,
                                    size = 26.0,
                                )
                            }
                        }
                    },
            )
        }
    }

    /** The search panel unfolded under the header: field plus source picker. */
    @Composable
    private fun _SearchPanel() {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("music-box-search-panel"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Input(
                modifier = Modifier.weight(1f).testTag("music-box-search-input"),
                controller = widget.searchController,
                focusNode = _searchFocus,
                hintText = "搜索歌曲点歌",
                prefixIcon = Icons.Filled.Search,
                showClearButton = true,
                maxLines = 1,
                height = _musicBoxControlHeight.dp,
            )
            Spacer(Modifier.width(6.dp))
            SegmentedControl(
                modifier = Modifier.testTag("music-box-search-source"),
                height = _musicBoxControlHeight.dp,
                value = normalizedMusicBoxSource(widget.source),
                segments = musicBoxSources.map { source -> Segment(value = source.id, label = source.label) },
                onChanged = widget.onSourceChanged,
            )
        }
    }

    fun emit(items: MutableList<_MusicBoxListItem>, keys: _MusicBoxListKeys, query: String) {
        val queueItems = _items
        val hasQuery = widget.searchOpen && query.trim().isNotEmpty()
        val currentItemId = if (_isActive) widget.state.playback.currentItemId else ""
        body.recordSectionHeader(MusicBoxSection.queue, items.size)
        items += _MusicBoxListItem(keys.key("queue-header")) { _Header() }
        if (widget.searchOpen) items += _MusicBoxListItem(keys.key("queue-search-panel")) { _SearchPanel() }
        when {
            hasQuery ->
                _MusicBoxSearchResults(
                    results = widget.searchResults,
                    query = query,
                    searching = widget.searching,
                    error = widget.searchError,
                    source = widget.source,
                    controller = widget.controller,
                    roomId = widget.roomId,
                    temporaryQueue = widget.state.temporaryQueue,
                    onQueueResult = widget.onQueueResult,
                ).emit(items, keys)
            queueItems.isEmpty() ->
                items +=
                    _MusicBoxListItem(keys.key("queue-empty")) {
                        _MusicBoxSliverBox {
                            _MusicBoxEmpty(icon = Icons.AutoMirrored.Filled.QueueMusic, message = "点歌队列为空", hint = "点击右上角的搜索按钮点歌")
                        }
                    }
            else -> {
                val firstRow = items.size
                for ((index, item) in queueItems.withIndex()) {
                    val isCurrent = item.id.isNotEmpty() && item.id == currentItemId
                    items +=
                        _MusicBoxListItem(keys.key("queue:${item.id}")) {
                            Box(Modifier.testTag("music-box-queue-list")) { _QueueRow(item, index, isCurrent) }
                        }
                }
                if (!hasQuery) {
                    val currentIndex = if (currentItemId.isEmpty()) -1 else queueItems.indexOfFirst { it.id == currentItemId }
                    _scheduleRevealCurrent(currentItemId, if (currentIndex < 0) -1 else firstRow + currentIndex)
                }
            }
        }
        items += _MusicBoxListItem(keys.key("queue-bottom-gap")) { Spacer(Modifier.height(8.dp)) }
    }

    @Composable
    private fun _QueueRow(item: MusicBoxQueueItem, index: Int, isCurrent: Boolean) {
        val canWrite = widget.controller != null && widget.roomId != null
        // Active queue: play-now needs playable audio (ready, or already
        // streaming) and the server's per-item permission. Inactive queue: any
        // song can switch the room back to the request queue, subject to the
        // switch capability.
        val canPlay =
            canWrite && !isCurrent && (if (_isActive) item.canPlayNow && item.playable else widget.state.canSwitchTemporary)
        val requester = item.requestedBy
        val state = widget.state
        _MusicBoxTrackRow(
            modifier = Modifier.testTag("music-box-queue-tile:${item.id}"),
            leading = { _MusicBoxQueueRowLeading(item = item, index = index, isCurrent = isCurrent) },
            title = item.title,
            subtitle = _musicBoxQueueSubtitle(item),
            subtitleColor = if (item.status == MusicBoxQueueItemStatus.failed) UiColors.danger else UiColors.textSecondary,
            selected = isCurrent,
            cardResetKey =
                listOf(
                    item.id,
                    item.status,
                    item.playable,
                    item.canPlayNow,
                    requester?.avatarUrl,
                    requester?.avatarLabel,
                    state.activeSource.type,
                    state.activeSource.name,
                ),
            cardBuilder = { _ ->
                _MusicBoxSongCardQueue(
                    item = item,
                    isCurrent = isCurrent,
                    queueSongCount = _items.size,
                    controller = widget.controller,
                    roomId = widget.roomId,
                    activeSource = state.activeSource,
                    onStateChanged = widget.onStateChanged,
                    currentUser = widget.currentUser,
                    onResolveUserProfile = widget.onResolveUserProfile,
                    onResolveRoomProfile = widget.onResolveRoomProfile,
                    onEnterCommonRoom = widget.onEnterCommonRoom,
                    userProfileActionBuilder = widget.userProfileActionBuilder,
                )
            },
            trailing =
                buildList {
                    if (requester != null) {
                        add {
                            _MusicBoxRequesterAvatar(
                                modifier = Modifier.testTag("music-box-queue-requester:${item.id}"),
                                requester = requester,
                                currentUser = widget.currentUser,
                                onResolveUserProfile = widget.onResolveUserProfile,
                                onResolveRoomProfile = widget.onResolveRoomProfile,
                                onEnterCommonRoom = widget.onEnterCommonRoom,
                                userProfileActionBuilder = widget.userProfileActionBuilder,
                            )
                        }
                    }
                    if (canPlay) {
                        add {
                            _MusicBoxRowAction(
                                modifier = Modifier.testTag("music-box-queue-play-now:${item.id}"),
                                icon = Icons.Filled.PlayArrow,
                                label = if (_isActive) "优先播放" else "从这首歌开始播放点歌队列",
                                tone = ButtonTone.primary,
                                loading = _playingNowItemId == item.id,
                                onPressed = { unawaited { _playItem(item) } },
                            )
                        }
                    }
                    if (item.canRemove) {
                        add {
                            _MusicBoxRowAction(
                                modifier = Modifier.testTag("music-box-queue-remove:${item.id}"),
                                icon = Icons.Filled.Close,
                                label = "从点歌队列删除",
                                tone = ButtonTone.danger,
                                onPressed = { widget.onRemoveItem(item) },
                            )
                        }
                    }
                },
        )
    }
}

internal fun _musicBoxQueueSubtitle(item: MusicBoxQueueItem): String {
    val artist = if (item.artist.trim().isEmpty()) "未知艺人" else item.artist.trim()
    val status = musicBoxQueueStatusLabel(item)
    return if (status == null) artist else "$artist · $status"
}

/**
 * A collapsed group of saved playlists (room or personal). Loads its list the
 * first time it is opened; each playlist row can unfold its tracks in place.
 * If the active source belongs to this scope but is not in the list (someone
 * else's personal playlist, or a template deleted after activation) it is
 * pinned on top so the playing snapshot always has a row.
 */
internal class _MusicBoxPlaylistGroupHolder(private val body: _MusicBoxBodyState, val type: MusicBoxActiveSourceType) {
    private var _loading = false
    private var _loaded = false
    private var _error: String? = null
    private var _playlists: List<PersonalMusicPlaylist> = emptyList()
    private var _loadGeneration = 0
    private var _disposed = false

    // The configuration of the last build (Dart's `oldWidget`).
    private var _open = false
    private var _roomId: String? = null
    private var _controller: MusicBoxController? = null
    private var _playlistsRevision = 0

    private val _tracks = LinkedHashMap<String, _MusicBoxPlaylistTracksHolder>()

    val roomScoped: Boolean get() = type == MusicBoxActiveSourceType.roomPlaylist
    val section: MusicBoxSection get() = musicBoxSectionForSource(type)
    val widget: _MusicBoxBodyWidget get() = body.widget
    val mounted: Boolean get() = !_disposed && body.mounted
    val bodyState: _MusicBoxBodyState get() = body

    private val open: Boolean get() = widget.openSections.contains(section)

    fun setState(fn: () -> Unit) {
        if (!mounted) return
        body.sectionSetState(fn)
    }

    private fun _snapshotConfiguration() {
        _open = open
        _roomId = widget.roomId
        _controller = widget.controller
        _playlistsRevision = widget.playlistsRevision
    }

    fun init() {
        _snapshotConfiguration()
        if (_open) unawaited { _load() }
    }

    fun update() {
        val identityChanged = _roomId != widget.roomId || _controller !== widget.controller
        if (identityChanged) {
            _loaded = false
            _playlists = emptyList()
        }
        val needsLoad = open && (identityChanged || !_loaded || _playlistsRevision != widget.playlistsRevision)
        _snapshotConfiguration()
        if (needsLoad && !_loading) unawaited { _load() }
    }

    fun dispose() {
        _disposed = true
        for (tracks in _tracks.values) tracks.dispose()
        _tracks.clear()
    }

    suspend fun _load() {
        val generation = ++_loadGeneration
        val controller = widget.controller
        val roomId = widget.roomId
        if (controller == null || (roomScoped && roomId == null)) {
            setState {
                _loading = false
                _loaded = true
                _error = "歌单服务暂不可用"
            }
            return
        }
        setState {
            _loading = true
            _error = null
        }
        try {
            val page = if (roomScoped) controller.loadRoomPlaylists(roomId = roomId!!) else controller.loadMyPlaylists()
            if (!mounted || generation != _loadGeneration) return
            setState {
                _loading = false
                _loaded = true
                _playlists = page.playlists
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted || generation != _loadGeneration) return
            setState {
                _loading = false
                _loaded = true
                _error = "加载歌单失败"
            }
        }
    }

    @Composable
    private fun _LeadingCard(playlist: PersonalMusicPlaylist, playing: Boolean, icon: @Composable () -> Unit) {
        val active = widget.state.activeSource
        val isActive = musicBoxSourceIsActive(widget.state, type, playlist.id)
        val creator =
            when {
                roomScoped -> null
                isActive -> _musicBoxActiveSourceOwner(active, widget.currentUser)
                else -> widget.currentUser?.toSummary()
            }
        val onEdit = if (roomScoped) widget.onEditRoomPlaylist else widget.onEditPersonalPlaylist
        MusicPlaylistHoverCard(
            modifier = Modifier.testTag("music-box-playlist-card-anchor:${playlist.id}"),
            data =
                MusicPlaylistCardData(
                    id = playlist.id,
                    name = playlist.name,
                    songCount = playlist.itemCount,
                    createdAt = playlist.createdAt ?: (if (isActive) active.createdAt else null),
                    creator = creator,
                    room = if (roomScoped) widget.room else null,
                    showPlayingStatus = playing,
                ),
            currentUser = widget.currentUser,
            onResolveUserProfile = widget.onResolveUserProfile,
            onResolveRoomProfile = widget.onResolveRoomProfile,
            onEnterCommonRoom = widget.onEnterCommonRoom,
            userProfileActionBuilder = widget.userProfileActionBuilder,
            // Playback starts from a song inside the playlist, never from the
            // playlist as a whole: the card offers no play-all.
            onPlayAll = null,
            showPrimaryActionWhenDisabled = false,
            onViewPlaylist = if (onEdit == null) ({ widget.onTogglePlaylist(type, playlist.id) }) else ({ onEdit(playlist.id) }),
            secondaryActionLabel = if (onEdit == null) "查看歌单" else "编辑歌单",
            secondaryActionIcon = if (onEdit == null) Icons.AutoMirrored.Filled.QueueMusic else Icons.Outlined.Edit,
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
        }
    }

    private fun _entries(): List<PersonalMusicPlaylist> {
        val state = widget.state
        val active = state.activeSource
        val activeInScope = active.type == type && active.id.isNotEmpty()
        val activeListed = activeInScope && _playlists.any { playlist -> playlist.id == active.id }
        return buildList {
            if (activeInScope && !activeListed) {
                add(
                    PersonalMusicPlaylist(
                        id = active.id,
                        name = musicBoxActiveSourceLabel(active),
                        description = "",
                        revision = 0,
                        itemCount = state.queue.size,
                        createdAt = active.createdAt,
                        updatedAt = null,
                    ),
                )
            }
            addAll(_playlists)
        }
    }

    fun emit(items: MutableList<_MusicBoxListItem>, keys: _MusicBoxListKeys) {
        val typeValue = musicBoxActiveSourceTypeValue(type)
        val active = widget.state.activeSource
        val playing = active.type == type && musicBoxHasActivePlayback(widget.state)
        val open = this.open
        val count = if (_loaded && _error == null) _entries().size else null
        body.recordSectionHeader(section, items.size)
        items +=
            _MusicBoxListItem(keys.key("group-header:$typeValue")) {
                _MusicBoxGroupHeader(
                    modifier = Modifier.testTag("music-box-group-header:$typeValue"),
                    channel = if (roomScoped) "02" else "03",
                    title = if (roomScoped) "房间歌单" else "我的歌单",
                    icon = if (roomScoped) Icons.Filled.MeetingRoom else Icons.Filled.Person,
                    open = open,
                    playing = playing,
                    count = count,
                    onToggle = { widget.onToggleSection(section) },
                )
            }
        val openTracks = HashSet<String>()
        if (open) {
            items += _MusicBoxListItem(keys.key("group-gap:$typeValue")) { Spacer(Modifier.height(2.dp)) }
            _emitBody(items, keys, openTracks)
        }
        // Tracks of playlists no longer unfolded (or no longer listed) leave
        // the tree, as Dart's keyed sliver does.
        val stale = _tracks.keys.filter { it !in openTracks }
        for (id in stale) _tracks.remove(id)?.dispose()
    }

    private fun _emitBody(items: MutableList<_MusicBoxListItem>, keys: _MusicBoxListKeys, openTracks: MutableSet<String>) {
        val typeValue = musicBoxActiveSourceTypeValue(type)
        if (_loading && !_loaded) {
            items += _MusicBoxListItem(keys.key("group-loading:$typeValue")) { _MusicBoxSliverBox { _MusicBoxLoading() } }
            return
        }
        val entries = _entries()
        val error = _error
        if (error != null) {
            // The playing snapshot needs no list load, so its pinned row stays
            // reachable under the retry state.
            items +=
                _MusicBoxListItem(keys.key("group-retry:$typeValue")) {
                    _MusicBoxSliverBox { _MusicBoxRetryState(message = error, onRetry = { unawaited { _load() } }) }
                }
            _emitRows(items, keys, entries, openTracks)
            return
        }
        if (entries.isEmpty()) {
            val onCreate = if (roomScoped) widget.onCreateFirstRoomPlaylist else widget.onCreateFirstPersonalPlaylist
            items +=
                _MusicBoxListItem(keys.key("group-empty:$typeValue")) {
                    _MusicBoxSliverBox(height = 140.0) {
                        _MusicBoxEmpty(
                            icon = if (roomScoped) Icons.Filled.MeetingRoom else Icons.Filled.Person,
                            message = if (roomScoped) "还没有房间歌单" else "还没有个人歌单",
                            actionKey = if (roomScoped) "music-box-create-first-room-playlist" else "music-box-create-first-personal-playlist",
                            actionLabel = if (onCreate == null) null else "新建第一个歌单",
                            onAction = onCreate,
                        )
                    }
                }
            return
        }
        _emitRows(items, keys, entries, openTracks)
    }

    private fun _emitRows(
        items: MutableList<_MusicBoxListItem>,
        keys: _MusicBoxListKeys,
        entries: List<PersonalMusicPlaylist>,
        openTracks: MutableSet<String>,
    ) {
        val state = widget.state
        val active = state.activeSource
        val hasActivePlayback = musicBoxHasActivePlayback(state)
        val typeValue = musicBoxActiveSourceTypeValue(type)
        for (playlist in entries) {
            val isActive = active.type == type && playlist.id == active.id
            val playing = isActive && hasActivePlayback
            val open = widget.openPlaylistKey == musicBoxPlaylistKey(type, playlist.id)
            val tracks: _MusicBoxPlaylistTracksHolder? =
                if (open) {
                    openTracks += playlist.id
                    val existing = _tracks[playlist.id]
                    if (existing == null) {
                        val created = _MusicBoxPlaylistTracksHolder(this, playlist.id)
                        _tracks[playlist.id] = created
                        created.init()
                        created
                    } else {
                        existing.update()
                        existing
                    }
                } else {
                    null
                }
            items +=
                _MusicBoxListItem(keys.key("playlist:$typeValue:${playlist.id}")) {
                    _PlaylistBlock(playlist, typeValue = typeValue, playing = playing, open = open, tracks = tracks)
                }
        }
        items += _MusicBoxListItem(keys.key("group-bottom:$typeValue")) { Spacer(Modifier.height(4.dp)) }
    }

    /**
     * One playlist and, when unfolded, its tracks — framed as a single block
     * with a gap below it, so neighbouring playlists and the next group read
     * as separate things.
     */
    @Composable
    private fun _PlaylistBlock(
        playlist: PersonalMusicPlaylist,
        typeValue: String,
        playing: Boolean,
        open: Boolean,
        tracks: _MusicBoxPlaylistTracksHolder?,
    ) {
        // Every playlist is the same framed block; unfolding only adds the
        // tracks inside it, so folded and unfolded rows read as one kind of thing.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .decoration(UiColors.surfaceLow, RoundedCornerShape(UiRadii.md), if (open) UiColors.borderStrong else UiColors.border)
                .padding(3.dp),
        ) {
            _MusicBoxPlaylistRow(
                modifier = Modifier.testTag("music-box-playlist-row:$typeValue:${playlist.id}"),
                name = playlist.name,
                itemCount = playlist.itemCount,
                playing = playing,
                open = open,
                icon = if (roomScoped) Icons.Filled.MeetingRoom else Icons.Filled.Person,
                onOpen = { widget.onTogglePlaylist(type, playlist.id) },
                hoverColor = UiColors.surface,
                leadingCard = { icon -> _LeadingCard(playlist, playing, icon) },
            )
            if (open && tracks != null) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().testTag("music-box-playlist-tracks:${playlist.id}")) { tracks.Content() }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

/**
 * The tracks of one unfolded playlist, indented under its row. When the
 * playlist is the active source the rows are the authoritative activation
 * snapshot (live status, play-now); otherwise the saved template is loaded
 * and each row can start the playlist from that song. Presets are played
 * from here and edited elsewhere, so there are no request/add actions.
 */
internal class _MusicBoxPlaylistTracksHolder(private val group: _MusicBoxPlaylistGroupHolder, private val playlistId: String) {
    private var _loading = false
    private var _error: String? = null
    private var _items: List<PersonalMusicPlaylistItem> = emptyList()
    private var _loadGeneration = 0
    private var _startingItemId: String? = null
    private var _disposed = false

    // The configuration of the last build (Dart's `oldWidget`).
    private var _state: MusicBoxState? = null
    private var _roomId: String? = null
    private var _controller: MusicBoxController? = null
    private var _playlistsRevision = 0

    private val type: MusicBoxActiveSourceType get() = group.type
    private val roomScoped: Boolean get() = group.roomScoped
    private val widget: _MusicBoxBodyWidget get() = group.widget
    private val mounted: Boolean get() = !_disposed && group.mounted

    private val _isActive: Boolean get() = musicBoxSourceIsActive(widget.state, type, playlistId)

    private fun setState(fn: () -> Unit) {
        if (!mounted) return
        group.setState(fn)
    }

    private fun _snapshotConfiguration() {
        _state = widget.state
        _roomId = widget.roomId
        _controller = widget.controller
        _playlistsRevision = widget.playlistsRevision
    }

    fun init() {
        _snapshotConfiguration()
        if (!_isActive) unawaited { _load() }
    }

    fun update() {
        val oldState = _state
        val wasActive = oldState != null && musicBoxSourceIsActive(oldState, type, playlistId)
        val changed =
            _roomId != widget.roomId ||
                _controller !== widget.controller ||
                _playlistsRevision != widget.playlistsRevision ||
                (wasActive && !_isActive)
        _snapshotConfiguration()
        if (changed && !_isActive) unawaited { _load() }
    }

    fun dispose() {
        _disposed = true
    }

    private suspend fun _load() {
        val generation = ++_loadGeneration
        val controller = widget.controller
        val roomId = widget.roomId
        if (controller == null || (roomScoped && roomId == null)) {
            setState {
                _loading = false
                _error = "歌单服务暂不可用"
            }
            return
        }
        setState {
            _loading = true
            _error = null
        }
        try {
            val page =
                if (roomScoped) {
                    controller.loadRoomPlaylist(roomId = roomId!!, playlistId = playlistId)
                } else {
                    controller.loadMyPlaylist(playlistId = playlistId)
                }
            if (!mounted || generation != _loadGeneration) return
            setState {
                _loading = false
                _items = page.items
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted || generation != _loadGeneration) return
            setState {
                _loading = false
                _error = "加载歌曲失败"
            }
        }
    }

    private suspend fun _activateFrom(item: PersonalMusicPlaylistItem) {
        val controller = widget.controller
        val roomId = widget.roomId
        if (controller == null || roomId == null || _startingItemId != null) return
        setState { _startingItemId = item.id }
        _musicBoxRunWrite(
            group.bodyState.context,
            write = { controller.activatePlaylist(roomId = roomId, sourceType = type, playlistId = playlistId, startItemId = item.id) },
            onStateChanged = widget.onStateChanged,
            failureMessage = "播放歌曲失败，请重试",
        )
        if (mounted) setState { _startingItemId = null }
    }

    private suspend fun _playNow(item: MusicBoxQueueItem) {
        val controller = widget.controller
        val roomId = widget.roomId
        if (controller == null || roomId == null || _startingItemId != null) return
        setState { _startingItemId = item.id }
        _musicBoxRunWrite(
            group.bodyState.context,
            write = { controller.playNow(roomId = roomId, item = item) },
            onStateChanged = widget.onStateChanged,
            failureMessage = "优先播放失败，请重试",
            successMessage = "已优先播放",
        )
        if (mounted) setState { _startingItemId = null }
    }

    @Composable
    private fun _Indented(row: @Composable () -> Unit) {
        Box(Modifier.fillMaxWidth().padding(start = _indent.dp)) { row() }
    }

    @Composable
    private fun _SnapshotRow(item: MusicBoxQueueItem, index: Int) {
        val state = widget.state
        val isCurrent = item.id.isNotEmpty() && item.id == state.playback.currentItemId
        val canPlayNow = widget.controller != null && widget.roomId != null && item.canPlayNow && item.playable && !isCurrent
        _Indented {
            _MusicBoxTrackRow(
                modifier = Modifier.testTag("music-box-queue-tile:${item.id}"),
                hoverColor = UiColors.surface,
                leading = { _MusicBoxQueueRowLeading(item = item, index = index, isCurrent = isCurrent) },
                title = item.title,
                subtitle = _musicBoxQueueSubtitle(item),
                subtitleColor = if (item.status == MusicBoxQueueItemStatus.failed) UiColors.danger else UiColors.textSecondary,
                selected = isCurrent,
                cardResetKey = listOf(item.id, item.status, item.playable, item.canPlayNow),
                cardBuilder = { _ ->
                    _MusicBoxSongCardQueue(
                        item = item,
                        isCurrent = isCurrent,
                        queueSongCount = state.queue.size,
                        controller = widget.controller,
                        roomId = widget.roomId,
                        activeSource = state.activeSource,
                        onStateChanged = widget.onStateChanged,
                        currentUser = widget.currentUser,
                        onResolveUserProfile = widget.onResolveUserProfile,
                        onResolveRoomProfile = widget.onResolveRoomProfile,
                        onEnterCommonRoom = widget.onEnterCommonRoom,
                        userProfileActionBuilder = widget.userProfileActionBuilder,
                    )
                },
                trailing =
                    buildList {
                        if (canPlayNow) {
                            add {
                                _MusicBoxRowAction(
                                    modifier = Modifier.testTag("music-box-queue-play-now:${item.id}"),
                                    icon = Icons.Filled.PlayArrow,
                                    label = "优先播放",
                                    tone = ButtonTone.primary,
                                    loading = _startingItemId == item.id,
                                    onPressed = { unawaited { _playNow(item) } },
                                )
                            }
                        }
                    },
            )
        }
    }

    @Composable
    private fun _TemplateRow(item: PersonalMusicPlaylistItem, index: Int) {
        val result = _musicBoxPlaylistItemAsResult(item)
        val identity = "${item.source}:${item.trackId}"
        val artists = musicBoxArtistsLabel(item.artists)
        val canWrite = widget.controller != null && widget.roomId != null
        _Indented {
            _MusicBoxTrackRow(
                modifier = Modifier.testTag("music-box-playlist-track:$identity"),
                hoverColor = UiColors.surface,
                leading = {
                    Text(
                        "${index + 1}",
                        style = uiTextStyle(color = UiColors.textMuted, fontSize = 11.0, fontWeight = FontWeight.W600).copy(fontFeatureSettings = "tnum"),
                    )
                },
                title = item.title,
                subtitle = if (artists.isEmpty()) "未知艺人" else artists,
                cardResetKey = identity,
                // Information only: presets are not requested from here.
                cardBuilder = { _ ->
                    _MusicBoxSongCardSearch(
                        result = result,
                        controller = widget.controller,
                        roomId = widget.roomId,
                        onQueueResult = null,
                        alreadyInRequestQueue = false,
                        catalogDurationMs = item.durationMs,
                    )
                },
                trailing =
                    listOf {
                        _MusicBoxRowAction(
                            modifier = Modifier.testTag("music-box-playlist-track-play:$identity"),
                            icon = Icons.Filled.PlayArrow,
                            label = "从这首歌开始播放歌单",
                            tone = ButtonTone.primary,
                            loading = _startingItemId == item.id,
                            onPressed = if (canWrite) ({ unawaited { _activateFrom(item) } }) else null,
                        )
                    },
            )
        }
    }

    @Composable
    fun Content() {
        if (_isActive) {
            val queue = widget.state.queue
            if (queue.isEmpty()) {
                _MusicBoxSliverBox(height = 96.0) { _MusicBoxEmpty(icon = Icons.AutoMirrored.Filled.QueueMusic, message = "这个歌单还没有歌曲") }
                return
            }
            Column(Modifier.fillMaxWidth()) {
                for ((index, item) in queue.withIndex()) _SnapshotRow(item, index)
            }
            return
        }
        if (_loading) {
            _MusicBoxSliverBox(height = 96.0) { _MusicBoxLoading() }
            return
        }
        val error = _error
        if (error != null) {
            _MusicBoxSliverBox { _MusicBoxRetryState(message = error, onRetry = { unawaited { _load() } }) }
            return
        }
        if (_items.isEmpty()) {
            _MusicBoxSliverBox(height = 96.0) { _MusicBoxEmpty(icon = Icons.AutoMirrored.Filled.QueueMusic, message = "这个歌单还没有歌曲") }
            return
        }
        Column(Modifier.fillMaxWidth()) {
            for ((index, item) in _items.withIndex()) _TemplateRow(item, index)
        }
    }

    companion object {
        private const val _indent = 12.0
    }
}

/**
 * Search hits for the current query, rendered as rows under the queue
 * header. The only action is "加入点歌队列"; a track already in the queue shows
 * a disabled check instead of failing after the tap.
 */
private class _MusicBoxSearchResults(
    val results: List<MusicBoxSearchResult>,
    val query: String,
    val searching: Boolean,
    val error: String?,
    val source: String,
    val controller: MusicBoxController?,
    val roomId: String?,
    val temporaryQueue: List<MusicBoxQueueItem>,
    val onQueueResult: (MusicBoxSearchResult) -> Unit,
) {
    fun emit(items: MutableList<_MusicBoxListItem>, keys: _MusicBoxListKeys) {
        val message = error?.trim()
        val sourceLabel = musicBoxSourceLabel(normalizedMusicBoxSource(source))
        items +=
            _MusicBoxListItem(keys.key("search-header")) {
                Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    _MusicBoxSectionHeader(
                        title = if (searching) "正在搜索 $sourceLabel…" else "搜索结果 · $sourceLabel · ${results.size} 首",
                    )
                }
            }
        when {
            searching && results.isEmpty() ->
                items += _MusicBoxListItem(keys.key("search-loading")) { _MusicBoxSliverBox { _MusicBoxLoading() } }
            message != null && message.isNotEmpty() ->
                items +=
                    _MusicBoxListItem(keys.key("search-error")) {
                        _MusicBoxSliverBox { _MusicBoxEmpty(icon = Icons.Filled.ErrorOutline, message = "搜索失败", hint = message) }
                    }
            results.isEmpty() ->
                items +=
                    _MusicBoxListItem(keys.key("search-empty")) {
                        _MusicBoxSliverBox {
                            _MusicBoxEmpty(icon = Icons.Filled.SearchOff, message = "没有找到相关歌曲", hint = "换个关键词，或切换搜索来源试试")
                        }
                    }
            else ->
                for (result in results) {
                    items +=
                        _MusicBoxListItem(keys.key("search:${result.source}:${result.trackId}")) {
                            Box(Modifier.testTag("music-box-search-results-list")) { _Row(result) }
                        }
                }
        }
    }

    @Composable
    private fun _Row(result: MusicBoxSearchResult) {
        val identity = "${result.source}:${result.trackId}"
        val artists = musicBoxArtistsLabel(result.artists)
        val alreadyQueued = musicBoxRequestQueueContainsTrack(temporaryQueue, source = result.source, trackId = result.trackId)
        _MusicBoxTrackRow(
            modifier = Modifier.testTag("music-box-search-tile:$identity"),
            leading = { Icon(Icons.Filled.MusicNote, size = 18.dp, color = UiColors.accent) },
            title = result.name,
            subtitle = if (artists.isEmpty()) "未知艺人" else artists,
            query = query,
            cardResetKey = "$identity:$alreadyQueued",
            cardBuilder = { _ ->
                _MusicBoxSongCardSearch(
                    result = result,
                    controller = controller,
                    roomId = roomId,
                    onQueueResult = if (alreadyQueued) null else onQueueResult,
                    alreadyInRequestQueue = alreadyQueued,
                )
            },
            trailing =
                listOf {
                    _MusicBoxRowAction(
                        modifier = Modifier.testTag("music-box-search-add:$identity"),
                        icon = if (alreadyQueued) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.Filled.Add,
                        label = if (alreadyQueued) "已在点歌队列" else "加入点歌队列",
                        tone = if (alreadyQueued) ButtonTone.neutral else ButtonTone.primary,
                        onPressed = if (alreadyQueued) null else ({ onQueueResult(result) }),
                    )
                },
        )
    }
}
