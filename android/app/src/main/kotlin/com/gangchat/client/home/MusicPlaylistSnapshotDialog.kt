package com.gangchat.client.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.gangchat.client.app.MusicTrackPreviewController
import com.gangchat.client.app.musicBoxSourceLabel
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.MusicTrackPreviewApi
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PersonalMusicPlaylistPage
import com.gangchat.client.app.MusicTrackPreviewPlatformFactory
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.MusicPlaylistTrackSurface
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.ui.showFloatingSuccessNotice
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.MediaQuery
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.rememberBuildContext
import kotlinx.coroutines.CancellationException

typealias MusicPlaylistSnapshotLoader = suspend () -> PersonalMusicPlaylistPage
typealias MusicPlaylistSnapshotAddTrack = suspend (track: MusicTrackCardData, playlist: PersonalMusicPlaylist) -> Unit
typealias MusicPlaylistSnapshotClone = suspend () -> PersonalMusicPlaylist

/**
 * A reusable, immutable playlist snapshot viewer.
 *
 * Both the active music-box playlist and playlists embedded in chat use this
 * surface so song-card interaction, preview lifecycle and clone limits remain
 * consistent regardless of where the playlist was opened. The `*Key`
 * parameters become test tags (Dart's widget keys).
 */
@Composable
fun MusicPlaylistSnapshotDialog(
    title: String,
    tracks: List<MusicTrackCardData>,
    loadPersonalPlaylists: MusicPlaylistSnapshotLoader,
    onAddToPlaylist: MusicPlaylistSnapshotAddTrack,
    previewPlatformFactory: MusicTrackPreviewPlatformFactory?,
    previewApi: MusicTrackPreviewApi? = null,
    onClone: MusicPlaylistSnapshotClone? = null,
    contentKey: String? = null,
    trackListKey: String? = null,
    cloneButtonKey: String? = null,
    doneButtonKey: String? = null,
    cloneSuccessMessage: ((PersonalMusicPlaylist) -> String)? = null,
    cloneErrorMessage: ((Throwable) -> String)? = null,
    cloneErrorFallback: String = "克隆歌单失败，请重试",
) = StatefulWidget(
    MusicPlaylistSnapshotDialogWidget(
        title, tracks, loadPersonalPlaylists, onAddToPlaylist, previewApi, previewPlatformFactory, onClone, contentKey, trackListKey,
        cloneButtonKey, doneButtonKey, cloneSuccessMessage, cloneErrorMessage, cloneErrorFallback,
    ),
) { _MusicPlaylistSnapshotDialogState() }

class MusicPlaylistSnapshotDialogWidget(
    val title: String,
    val tracks: List<MusicTrackCardData>,
    val loadPersonalPlaylists: MusicPlaylistSnapshotLoader,
    val onAddToPlaylist: MusicPlaylistSnapshotAddTrack,
    val previewApi: MusicTrackPreviewApi?,
    val previewPlatformFactory: MusicTrackPreviewPlatformFactory?,
    val onClone: MusicPlaylistSnapshotClone?,
    val contentKey: String?,
    val trackListKey: String?,
    val cloneButtonKey: String?,
    val doneButtonKey: String?,
    val cloneSuccessMessage: ((PersonalMusicPlaylist) -> String)?,
    val cloneErrorMessage: ((Throwable) -> String)?,
    val cloneErrorFallback: String,
)

private class _MusicPlaylistSnapshotDialogState : ViewState<MusicPlaylistSnapshotDialogWidget>() {
    private var _previewController: MusicTrackPreviewController? = null
    private var _personalPlaylists: List<PersonalMusicPlaylist> = emptyList()
    private var _maxPlaylists = 50
    private var _loadingPlaylists = true
    private var _cloning = false
    private var _clonedPlaylist: PersonalMusicPlaylist? = null

    private val _playlistLimitReached: Boolean get() = !_loadingPlaylists && _personalPlaylists.size >= _maxPlaylists

    override fun initState() {
        _createPreviewController()
        unawaited { _loadPlaylists() }
    }

    override fun didUpdateWidget(oldWidget: MusicPlaylistSnapshotDialogWidget) {
        if (oldWidget.previewApi !== widget.previewApi || oldWidget.previewPlatformFactory !== widget.previewPlatformFactory) {
            val previous = _previewController
            _previewController = null
            if (previous != null) unawaited { previous.dispose() }
            _createPreviewController()
        }
    }

    private fun _createPreviewController() {
        val api = widget.previewApi
        val factory = widget.previewPlatformFactory
        if (api != null && factory != null) {
            _previewController = MusicTrackPreviewController(api = api, platform = factory.create())
        }
    }

    override fun dispose() {
        val preview = _previewController
        if (preview != null) unawaited { preview.dispose() }
    }

    private suspend fun _loadPlaylists() {
        try {
            val page = widget.loadPersonalPlaylists()
            if (!mounted) return
            setState {
                _personalPlaylists = page.playlists
                _maxPlaylists = page.maxPlaylists
                _loadingPlaylists = false
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (mounted) setState { _loadingPlaylists = false }
        }
    }

    private suspend fun _clone() {
        val clone = widget.onClone
        if (clone == null || _cloning || _loadingPlaylists || _clonedPlaylist != null || _playlistLimitReached) return
        setState { _cloning = true }
        try {
            val playlist = clone()
            if (!mounted) return
            setState {
                _clonedPlaylist = playlist
                _personalPlaylists = _personalPlaylists + playlist
            }
            val notice = widget.cloneSuccessMessage?.invoke(playlist) ?: "已克隆到我的歌单 - ${playlist.name}"
            showFloatingSuccessNotice(context, notice)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (mounted) {
                showFloatingErrorNotice(
                    context,
                    widget.cloneErrorMessage?.invoke(error) ?: userFacingErrorMessage(error, fallback = widget.cloneErrorFallback),
                )
            }
        } finally {
            if (mounted) setState { _cloning = false }
        }
    }

    @Composable
    override fun build() {
        val canClone = widget.onClone != null && !_loadingPlaylists && !_playlistLimitReached && _clonedPlaylist == null
        val cloneLabel =
            when {
                _clonedPlaylist != null -> "已克隆到我的歌单"
                _loadingPlaylists -> "正在检查我的歌单"
                _playlistLimitReached -> "我的歌单已满"
                else -> "克隆到我的歌单"
            }
        val height = (MediaQuery.sizeOf(context).height * 0.68f).coerceIn(260.dp, 620.dp)
        DialogFrame(
            title = widget.title,
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            maxWidth = 760.dp,
            adaptiveActions =
                listOfNotNull(
                    if (widget.onClone != null) {
                        ResponsiveDialogAction(
                            buttonKey = widget.cloneButtonKey,
                            label = cloneLabel,
                            icon = Icons.Filled.LibraryAdd,
                            tone = ButtonTone.primary,
                            loading = _cloning,
                            onPressed = if (canClone) ({ unawaited { _clone() } }) else null,
                        )
                    } else {
                        null
                    },
                    ResponsiveDialogAction(
                        buttonKey = widget.doneButtonKey,
                        label = "完成",
                        icon = Icons.Filled.Check,
                        onPressed = { Navigator.of(context).pop() },
                    ),
                ),
            child = {
                Box(
                    Modifier
                        .then(if (widget.contentKey != null) Modifier.testTag(widget.contentKey!!) else Modifier)
                        .fillMaxWidth()
                        .height(height),
                ) {
                    if (widget.tracks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("这个歌单还没有歌曲", style = UiTypography.body.copyWith(color = UiColors.textMuted))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().then(if (widget.trackListKey != null) Modifier.testTag(widget.trackListKey!!) else Modifier),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(widget.tracks.size) { index ->
                                val track = widget.tracks[index]
                                MusicPlaylistSnapshotTrackTile(
                                    track = track,
                                    previewController = _previewController,
                                    playlists = _personalPlaylists,
                                    onAddToPlaylist = { playlist -> widget.onAddToPlaylist(track, playlist) },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

/** Test tag of a snapshot track row (Dart's `ValueKey('music-playlist-snapshot-track:$id')`). */
fun musicPlaylistSnapshotTrackTag(id: String) = "music-playlist-snapshot-track:$id"

@Composable
fun MusicPlaylistSnapshotTrackTile(
    track: MusicTrackCardData,
    previewController: MusicTrackPreviewController?,
    playlists: List<PersonalMusicPlaylist>,
    onAddToPlaylist: suspend (PersonalMusicPlaylist) -> Unit,
    modifier: Modifier = Modifier,
    surfaceKey: String? = null,
) {
    val subtitle =
        listOfNotNull(
            if (track.artists.isNotEmpty()) track.artists.joinToString("、") else null,
            musicBoxSourceLabel(track.source),
        ).joinToString(" · ")
    val context = rememberBuildContext()
    val surface: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val titleStyle = uiTextStyle(color = UiColors.text, fontSize = 13.0, fontWeight = FontWeight.W600)
            val subtitleStyle = uiTextStyle(color = UiColors.textMuted, fontSize = 12.0)
            val textWidth = (maxWidth - 58.dp).coerceAtLeast(40.dp)
            MusicPlaylistTrackSurface(modifier = Modifier.fillMaxWidth().testTag(surfaceKey ?: musicPlaylistSnapshotTrackTag(track.id))) {
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, size = 19.dp, color = UiColors.accent)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = _adaptiveListTextStyle(context, track.title, titleStyle, textWidth), softWrap = true)
                        Spacer(Modifier.height(3.dp))
                        Text(subtitle, style = _adaptiveListTextStyle(context, subtitle, subtitleStyle, textWidth), softWrap = true)
                    }
                }
            }
        }
    }
    val preview = previewController
    if (preview == null) {
        Box(modifier) { surface() }
        return
    }
    MusicTrackHoverCard(
        data = track,
        previewController = preview,
        playlists = playlists,
        onAddToPlaylist = onAddToPlaylist,
        modifier = modifier,
        child = surface,
    )
}

/**
 * Shrinks a list label by up to 2 sp when it would take more than two lines
 * at [width] (Dart measured the style as given, not merged with the ambient
 * text style; the rendered text merges it).
 */
private fun _adaptiveListTextStyle(context: BuildContext, text: String, baseStyle: TextStyle, width: Dp): TextStyle {
    val painter = TextPainter(if (text.isEmpty()) " " else text, baseStyle).layout(context, maxWidth = width)
    val lineCount = painter.lineCount
    if (lineCount <= 2) return baseStyle
    val baseSize = if (baseStyle.fontSize.isSpecified) baseStyle.fontSize.value.toDouble() else 12.0
    return baseStyle.copy(fontSize = (baseSize - (lineCount - 2).coerceIn(1, 2)).coerceIn(10.0, baseSize).sp)
}
