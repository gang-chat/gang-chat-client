package com.gangchat.client.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MusicTrackPreviewController
import com.gangchat.client.app.MusicTrackPreviewSnapshot
import com.gangchat.client.app.MusicTrackPreviewTrack
import com.gangchat.client.app.musicBoxArtistFieldLabel
import com.gangchat.client.app.musicBoxFormatDuration
import com.gangchat.client.app.musicBoxSourceLabel
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.ResponsiveDialogActionBar
import com.gangchat.client.ui.Segment
import com.gangchat.client.ui.SegmentedControl
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiSpacing
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.ui.showFloatingSuccessNotice
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.MediaQuery
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.StreamBuilder
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.rawScrollbar
import com.gangchat.client.widgets.rememberScrollbarAdapter
import com.gangchat.client.widgets.showDialog
import kotlinx.coroutines.CancellationException

class MusicTrackCardData(
    val id: String,
    val source: String,
    val trackId: String,
    val title: String,
    val artists: List<String>,
    val durationMs: Int,
) {
    val previewTrack: MusicTrackPreviewTrack get() = MusicTrackPreviewTrack(source = source, trackId = trackId)
}

enum class MusicTrackPlaylistTargetScope { personal, room }

/**
 * A writable playlist shown by a song card.
 *
 * Keeping the scope beside the playlist prevents room playlists from being
 * accidentally submitted through the personal-playlist API when a picker
 * contains both kinds of target.
 */
class MusicTrackPlaylistTarget private constructor(
    val playlist: PersonalMusicPlaylist,
    val scope: MusicTrackPlaylistTargetScope,
    val roomId: String?,
) {
    val roomScoped: Boolean get() = scope == MusicTrackPlaylistTargetScope.room

    val key: String get() = "${scope.name}:${roomId ?: ""}:${playlist.id}"

    companion object {
        fun personal(playlist: PersonalMusicPlaylist) = MusicTrackPlaylistTarget(playlist, MusicTrackPlaylistTargetScope.personal, null)

        fun room(playlist: PersonalMusicPlaylist, roomId: String) = MusicTrackPlaylistTarget(playlist, MusicTrackPlaylistTargetScope.room, roomId)
    }
}

/** Test tag of a song card (Dart's `ValueKey('music-track-card:$id')`). */
fun musicTrackCardTag(id: String) = "music-track-card:$id"

const val musicTrackCardPreviewTag = "music-track-card-preview"
const val musicTrackCardAddToPlaylistTag = "music-track-card-add-to-playlist"
const val musicTrackPlaylistTargetConfirmTag = "music-track-playlist-target-confirm"
const val musicTrackPlaylistTargetSearchTag = "music-track-playlist-target-search"

/** Test tag of a picker option (Dart's `ValueKey('music-track-playlist-target:...')`). */
fun musicTrackPlaylistTargetTag(target: MusicTrackPlaylistTarget) =
    "music-track-playlist-target:${if (target.roomScoped) target.key else target.playlist.id}"

/**
 * Song profile card for saved-playlist rows. Preview is local-only: it never
 * changes the room queue or authoritative music-box playback state.
 */
@Composable
fun MusicTrackHoverCard(
    data: MusicTrackCardData,
    previewController: MusicTrackPreviewController,
    modifier: Modifier = Modifier,
    playlists: List<PersonalMusicPlaylist> = emptyList(),
    loadPlaylists: (suspend () -> List<PersonalMusicPlaylist>)? = null,
    playlistsRoomScoped: Boolean = false,
    onAddToPlaylist: (suspend (PersonalMusicPlaylist) -> Unit)? = null,
    playlistTargets: List<MusicTrackPlaylistTarget> = emptyList(),
    loadPlaylistTargets: (suspend () -> List<MusicTrackPlaylistTarget>)? = null,
    playlistTargetsLoadKey: Any? = null,
    onAddToPlaylistTarget: (suspend (MusicTrackPlaylistTarget) -> Unit)? = null,
    child: @Composable () -> Unit,
) = StatefulWidget(
    MusicTrackHoverCardWidget(
        data, previewController, child, playlists, loadPlaylists, playlistsRoomScoped, onAddToPlaylist, playlistTargets,
        loadPlaylistTargets, playlistTargetsLoadKey, onAddToPlaylistTarget, modifier,
    ),
) { _MusicTrackHoverCardState() }

class MusicTrackHoverCardWidget(
    val data: MusicTrackCardData,
    val previewController: MusicTrackPreviewController,
    val child: @Composable () -> Unit,
    val playlists: List<PersonalMusicPlaylist>,
    val loadPlaylists: (suspend () -> List<PersonalMusicPlaylist>)?,
    val playlistsRoomScoped: Boolean,
    val onAddToPlaylist: (suspend (PersonalMusicPlaylist) -> Unit)?,
    val playlistTargets: List<MusicTrackPlaylistTarget>,
    val loadPlaylistTargets: (suspend () -> List<MusicTrackPlaylistTarget>)?,
    val playlistTargetsLoadKey: Any?,
    val onAddToPlaylistTarget: (suspend (MusicTrackPlaylistTarget) -> Unit)?,
    val modifier: Modifier,
)

private class _MusicTrackHoverCardState : ViewState<MusicTrackHoverCardWidget>() {
    override fun didUpdateWidget(oldWidget: MusicTrackHoverCardWidget) {
        if (oldWidget.data.previewTrack.key != widget.data.previewTrack.key || oldWidget.previewController !== widget.previewController) {
            val controller = oldWidget.previewController
            val key = oldWidget.data.previewTrack.key
            unawaited { controller.stopIf(key) }
        }
    }

    private suspend fun _loadPlaylistTargets(): List<MusicTrackPlaylistTarget> {
        val loadTargets = widget.loadPlaylistTargets
        val loadLegacy = widget.loadPlaylists
        if (loadTargets != null) return loadTargets()
        if (loadLegacy != null) return loadLegacy().map(::_legacyPlaylistTarget)
        if (widget.playlistTargets.isNotEmpty()) return widget.playlistTargets
        return widget.playlists.map(::_legacyPlaylistTarget)
    }

    private fun _legacyPlaylistTarget(playlist: PersonalMusicPlaylist): MusicTrackPlaylistTarget =
        if (widget.playlistsRoomScoped) {
            MusicTrackPlaylistTarget.room(playlist = playlist, roomId = "")
        } else {
            MusicTrackPlaylistTarget.personal(playlist)
        }

    override fun dispose() {
        val controller = widget.previewController
        val key = widget.data.previewTrack.key
        unawaited { controller.stopIf(key) }
    }

    private suspend fun _togglePreview() {
        try {
            widget.previewController.toggle(widget.data.previewTrack)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (mounted) showFloatingErrorNotice(context, userFacingErrorMessage(error, fallback = "试听失败，请重试"))
        }
    }

    private suspend fun _addToPlaylist(target: MusicTrackPlaylistTarget) {
        val addTarget = widget.onAddToPlaylistTarget
        val addLegacy = widget.onAddToPlaylist
        if (addTarget != null) {
            addTarget(target)
        } else if (addLegacy != null) {
            addLegacy(target.playlist)
        }
    }

    private suspend fun _openPlaylistPicker() {
        val target = showMusicTrackPlaylistTargetDialog(context, loadTargets = ::_loadPlaylistTargets, onAdd = ::_addToPlaylist)
        if (!mounted || target == null) return
        showFloatingSuccessNotice(context, "已添加到「${target.playlist.name}」")
    }

    @Composable
    override fun build() {
        val trackKey = widget.data.previewTrack.key
        val controller = widget.previewController
        HoverCardAnchor(
            modifier = widget.modifier,
            cardWidth = 304.dp,
            resetKey = listOf(widget.data.id, trackKey),
            onVisibilityChanged = { visible -> if (!visible) unawaited { controller.stopIf(trackKey) } },
            cardBuilder = { _ ->
                StreamBuilder<MusicTrackPreviewSnapshot>(stream = controller.changes, initialData = controller.snapshot) { snapshot ->
                    val preview = snapshot.data ?: controller.snapshot
                    _MusicTrackProfileCard(
                        data = widget.data,
                        loading = preview.isLoading(trackKey),
                        playing = preview.isPlaying(trackKey),
                        onTogglePreview = { unawaited { _togglePreview() } },
                        onOpenPlaylistPicker =
                            if (widget.onAddToPlaylist == null && widget.onAddToPlaylistTarget == null) {
                                null
                            } else {
                                { unawaited { _openPlaylistPicker() } }
                            },
                    )
                }
            },
            child = widget.child,
        )
    }
}

@Composable
private fun _MusicTrackProfileCard(
    data: MusicTrackCardData,
    loading: Boolean,
    playing: Boolean,
    onTogglePreview: () -> Unit,
    onOpenPlaylistPicker: (() -> Unit)?,
) {
    val artists = data.artists.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("、")
    val bilibili = data.source.trim().lowercase() == "bilibili"
    Column(Modifier.testTag(musicTrackCardTag(data.id)).fillMaxWidth().padding(EdgeInsets.all(UiSpacing.lg))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 2.dp)) { Icon(Icons.Filled.MusicNote, color = UiColors.accent, size = 24.dp) }
            Spacer(Modifier.width(UiSpacing.sm))
            HoverCardSelectableText(
                value = data.title,
                maxLines = 12,
                style = UiTypography.title.copyWith(fontSize = 16.0),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(UiSpacing.md))
        _MusicTrackDetailRow(label = "时长", value = if (data.durationMs > 0) musicBoxFormatDuration(data.durationMs) else "未知")
        _MusicTrackDetailRow(
            label = musicBoxArtistFieldLabel(data.source),
            value = if (artists.isEmpty()) (if (bilibili) "未知作者" else "未知歌手") else artists,
        )
        _MusicTrackDetailRow(label = "来源", value = musicBoxSourceLabel(data.source))
        if (bilibili) _MusicTrackDetailRow(label = "详情", value = _bilibiliTrackID(data.trackId))
        Spacer(Modifier.height(UiSpacing.md))
        ResponsiveDialogActionBar(
            expanded = true,
            modifier = Modifier.fillMaxWidth(),
            actions =
                listOfNotNull(
                    ResponsiveDialogAction(
                        label = if (playing) "取消试听" else "试听",
                        buttonKey = musicTrackCardPreviewTag,
                        icon = if (playing) Icons.Rounded.Stop else Icons.Filled.PlayArrow,
                        tone = if (playing) ButtonTone.danger else ButtonTone.primary,
                        loading = loading,
                        onPressed = if (loading) null else onTogglePreview,
                    ),
                    onOpenPlaylistPicker?.let {
                        ResponsiveDialogAction(
                            label = "添加到歌单",
                            buttonKey = musicTrackCardAddToPlaylistTag,
                            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                            tone = ButtonTone.primary,
                            onPressed = it,
                        )
                    },
                ),
        )
    }
}

private enum class _MusicTrackPlaylistTargetFilter { all, personal, room }

suspend fun showMusicTrackPlaylistTargetDialog(
    context: BuildContext,
    loadTargets: suspend () -> List<MusicTrackPlaylistTarget>,
    onAdd: suspend (MusicTrackPlaylistTarget) -> Unit,
): MusicTrackPlaylistTarget? =
    showDialog<MusicTrackPlaylistTarget>(context) { _ -> _MusicTrackPlaylistTargetDialog(loadTargets = loadTargets, onAdd = onAdd) }

@Composable
private fun _MusicTrackPlaylistTargetDialog(
    loadTargets: suspend () -> List<MusicTrackPlaylistTarget>,
    onAdd: suspend (MusicTrackPlaylistTarget) -> Unit,
) = StatefulWidget(_MusicTrackPlaylistTargetDialogWidget(loadTargets, onAdd)) { _MusicTrackPlaylistTargetDialogState() }

private class _MusicTrackPlaylistTargetDialogWidget(
    val loadTargets: suspend () -> List<MusicTrackPlaylistTarget>,
    val onAdd: suspend (MusicTrackPlaylistTarget) -> Unit,
)

private class _MusicTrackPlaylistTargetDialogState : ViewState<_MusicTrackPlaylistTargetDialogWidget>() {
    private val _searchController = TextEditingController()
    private val _searchFocusNode = FocusNode()
    private var _targets: List<MusicTrackPlaylistTarget> = emptyList()
    private var _filter = _MusicTrackPlaylistTargetFilter.all
    private var _selectedKey: String? = null
    private var _error: String? = null
    private var _loading = true
    private var _adding = false

    override fun initState() {
        unawaited { _load() }
    }

    private suspend fun _load() {
        try {
            val targets = widget.loadTargets()
            if (!mounted) return
            setState {
                _targets = targets
                _loading = false
            }
            // Dart focuses after the frame; the node defers until attached.
            _searchFocusNode.requestFocus()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _loading = false
                _error = userFacingErrorMessage(error, fallback = "加载歌单失败，请重试")
            }
        }
    }

    private val _visibleTargets: List<MusicTrackPlaylistTarget>
        get() {
            val query = _searchController.text.trim().lowercase()
            return _targets.filter { target ->
                val matchesScope =
                    when (_filter) {
                        _MusicTrackPlaylistTargetFilter.all -> true
                        _MusicTrackPlaylistTargetFilter.personal -> !target.roomScoped
                        _MusicTrackPlaylistTargetFilter.room -> target.roomScoped
                    }
                matchesScope && (query.isEmpty() || target.playlist.name.lowercase().contains(query))
            }
        }

    private val _selectedTarget: MusicTrackPlaylistTarget? get() = _targets.firstOrNull { it.key == _selectedKey }

    private suspend fun _confirm() {
        val target = _selectedTarget
        if (target == null || _adding) return
        setState { _adding = true }
        try {
            widget.onAdd(target)
            if (mounted) Navigator.of(context).pop(target)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (mounted) showFloatingErrorNotice(context, userFacingErrorMessage(error, fallback = "添加失败，请检查歌单权限"))
        } finally {
            if (mounted) setState { _adding = false }
        }
    }

    @Composable
    override fun build() {
        val visibleTargets = _visibleTargets
        val height = (MediaQuery.sizeOf(context).height * 0.58f).coerceIn(240.dp, 560.dp)
        val selectedTarget = _selectedTarget
        DialogFrame(
            title = "添加到歌单",
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            maxWidth = 620.dp,
            adaptiveActions =
                listOf(
                    ResponsiveDialogAction(label = "取消", onPressed = if (_adding) null else ({ Navigator.of(context).pop() })),
                    ResponsiveDialogAction(
                        buttonKey = musicTrackPlaylistTargetConfirmTag,
                        label = "确认添加",
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        tone = ButtonTone.primary,
                        loading = _adding,
                        onPressed = if (selectedTarget == null || _adding) null else ({ unawaited { _confirm() } }),
                    ),
                ),
            child = {
                Column(Modifier.height(height).fillMaxWidth()) {
                    Input(
                        modifier = Modifier.fillMaxWidth().testTag(musicTrackPlaylistTargetSearchTag),
                        controller = _searchController,
                        focusNode = _searchFocusNode,
                        prefixIcon = Icons.Filled.Search,
                        hintText = "搜索歌单",
                        showClearButton = true,
                        onChanged = { setState() },
                        textInputAction = ImeAction.Search,
                    )
                    Spacer(Modifier.height(UiSpacing.sm))
                    SegmentedControl(
                        expanded = true,
                        value = _filter,
                        onChanged = { value -> setState { _filter = value } },
                        segments =
                            listOf(
                                Segment(value = _MusicTrackPlaylistTargetFilter.all, label = "全部"),
                                Segment(value = _MusicTrackPlaylistTargetFilter.personal, label = "我的歌单"),
                                Segment(value = _MusicTrackPlaylistTargetFilter.room, label = "房间歌单"),
                            ),
                    )
                    val error = _error
                    if (error != null) {
                        Spacer(Modifier.height(UiSpacing.sm))
                        Text(error, style = UiTypography.label.copyWith(color = UiColors.danger))
                    }
                    Spacer(Modifier.height(UiSpacing.sm))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            _loading ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = UiColors.accent,
                                        strokeWidth = 4.dp,
                                        trackColor = Color.Transparent,
                                        strokeCap = StrokeCap.Square,
                                    )
                                }
                            visibleTargets.isEmpty() ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        if (_targets.isEmpty()) "还没有可用歌单" else "没有匹配的歌单",
                                        style = UiTypography.body.copyWith(color = UiColors.textMuted),
                                    )
                                }
                            else -> {
                                val listState = rememberLazyListState()
                                LazyColumn(
                                    state = listState,
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .rawScrollbar(
                                                rememberScrollbarAdapter(listState),
                                                thickness = 7.dp,
                                                radius = 999.dp,
                                                thumbColor = UiColors.textMuted.copy(alpha = 0.82f),
                                                interactive = true,
                                            ),
                                    contentPadding = PaddingValues(end = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(UiSpacing.sm),
                                ) {
                                    items(visibleTargets.size, key = { visibleTargets[it].key }) { index ->
                                        val target = visibleTargets[index]
                                        _MusicTrackPlaylistTargetOption(
                                            target = target,
                                            selected = target.key == _selectedKey,
                                            onPressed = if (_adding) null else ({ setState { _selectedKey = target.key } }),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun _MusicTrackPlaylistTargetOption(target: MusicTrackPlaylistTarget, selected: Boolean, onPressed: (() -> Unit)?) {
    val background by animateColorAsState(
        if (selected) UiColors.selected else UiColors.surfaceLow,
        tween(90, easing = Curves.linear),
        label = "playlistTargetBackground",
    )
    val border by animateColorAsState(
        if (selected) UiColors.selectedBorder else UiColors.border,
        tween(90, easing = Curves.linear),
        label = "playlistTargetBorder",
    )
    Row(
        Modifier
            .testTag(musicTrackPlaylistTargetTag(target))
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = onPressed != null,
                role = Role.Button,
                onClick = { onPressed?.invoke() },
            )
            .heightIn(min = 64.dp)
            .decoration(background, RoundedCornerShape(UiRadii.md), border)
            // Flutter's Container adds the 1 dp border to its 14 dp padding.
            .padding(EdgeInsets.all(15.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (target.roomScoped) Icons.Filled.MeetingRoom else Icons.Filled.Person, color = UiColors.accent, size = 20.dp)
        Spacer(Modifier.width(UiSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                target.playlist.name,
                softWrap = true,
                style = UiTypography.body.copyWith(color = UiColors.text, fontWeight = FontWeight.W600),
            )
            Spacer(Modifier.height(2.dp))
            Text("${target.playlist.itemCount} 首歌曲", style = UiTypography.label.copyWith(color = UiColors.textMuted))
        }
        Spacer(Modifier.width(UiSpacing.md))
        Icon(
            if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            color = if (selected) UiColors.accent else UiColors.textMuted,
            size = 20.dp,
        )
    }
}

@Composable
private fun _MusicTrackDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(38.dp)) {
            HoverCardSelectableText(value = label, style = UiTypography.label.copyWith(color = UiColors.textMuted))
        }
        HoverCardSelectableText(
            value = value,
            textAlign = TextAlign.End,
            maxLines = 12,
            style = UiTypography.label.copyWith(color = UiColors.text),
            modifier = Modifier.weight(1f),
        )
    }
}

private val _bvidPattern = Regex("BV[0-9A-Za-z]+", RegexOption.IGNORE_CASE)

private fun _bilibiliTrackID(trackId: String): String {
    val raw = trackId.trim()
    val match = _bvidPattern.find(raw)?.value
    if (match == null || match.length <= 2) return if (raw.isEmpty()) "未知" else raw
    return "BV${match.substring(2)}"
}
