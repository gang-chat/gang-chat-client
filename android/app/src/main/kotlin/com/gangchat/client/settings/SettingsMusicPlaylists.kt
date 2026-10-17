package com.gangchat.client.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MusicPlaylistManagementCapabilities
import com.gangchat.client.app.MusicTrackPreviewController
import com.gangchat.client.app.MusicTrackPreviewPlatformFactory
import com.gangchat.client.app.PersonalMusicPlaylistsController
import com.gangchat.client.app.PersonalPlaylistCreateDraft
import com.gangchat.client.app.PersonalPlaylistFilterDraft
import com.gangchat.client.app.PersonalPlaylistItemFilterDraft
import com.gangchat.client.app.filteredPersonalMusicPlaylists
import com.gangchat.client.app.musicBoxDefaultSource
import com.gangchat.client.app.musicBoxSources
import com.gangchat.client.app.normalizedPersonalPlaylistName
import com.gangchat.client.app.personalPlaylistAllVisibleSelected
import com.gangchat.client.app.personalPlaylistArtistsLabel
import com.gangchat.client.app.personalPlaylistCount11To50
import com.gangchat.client.app.personalPlaylistCount1To10
import com.gangchat.client.app.personalPlaylistCount51To100
import com.gangchat.client.app.personalPlaylistCountAll
import com.gangchat.client.app.personalPlaylistCountEmpty
import com.gangchat.client.app.personalPlaylistCountOver100
import com.gangchat.client.app.personalPlaylistFilterActive
import com.gangchat.client.app.personalPlaylistItemOrderWithSelectionPinnedToFront
import com.gangchat.client.app.personalPlaylistListFilterActive
import com.gangchat.client.app.personalPlaylistOrderWithSelectionPinnedToFront
import com.gangchat.client.app.personalPlaylistSelectionNumbers
import com.gangchat.client.app.personalPlaylistSourceLabel
import com.gangchat.client.app.publicRoomFromRoomDetail
import com.gangchat.client.app.toggledPersonalPlaylistListSelection
import com.gangchat.client.app.toggledPersonalPlaylistSelection
import com.gangchat.client.app.toggledVisiblePersonalPlaylistSelection
import com.gangchat.client.foundation.scheduleMicrotask
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.home.MusicTrackCardData
import com.gangchat.client.home.MusicTrackHoverCard
import com.gangchat.client.home.MusicTrackPlaylistTarget
import com.gangchat.client.home.RoomHoverCard
import com.gangchat.client.protocol.ApiException
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.MusicTrackPreviewApi
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PersonalMusicPlaylistBatchAddResult
import com.gangchat.client.protocol.PersonalMusicPlaylistItem
import com.gangchat.client.protocol.PersonalMusicPlaylistMergeResult
import com.gangchat.client.protocol.PersonalMusicPlaylistPage
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.newUuid
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonIconPlain
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.ButtonWidget
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.HighlightedText
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.MusicPlaylistTrackSurface
import com.gangchat.client.ui.PressableSurface
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.Segment
import com.gangchat.client.ui.SegmentedControl
import com.gangchat.client.ui.SettingsFixedHeaderCard
import com.gangchat.client.ui.SettingsList
import com.gangchat.client.ui.StickerActionGrid
import com.gangchat.client.ui.StickerActionGridEntry
import com.gangchat.client.ui.StickerConfirmDialog
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiPointerTapRegion
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.ui.showFloatingSuccessNotice
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.FocusScope
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.MediaQuery
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.rememberBuildContext
import com.gangchat.client.widgets.showDialog
import com.gangchat.client.widgets.startTimer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * `error.toString()` as Dart printed it: the playlist panel shows errors
 * verbatim. Kotlin's standard exceptions get Dart's prefixes instead of their
 * JVM class names (`StateError` -> "Bad state: ..."); exceptions with their
 * own `toString()` (ported Dart exceptions such as `ApiException`) keep it.
 */
internal fun _errorText(error: Any): String {
    if (error !is Throwable) return error.toString()
    val text = error.toString()
    val message = error.localizedMessage
    val className = error.javaClass.name
    val jvmDefault = if (message == null) className else "$className: $message"
    if (text != jvmDefault) return text
    fun dart(prefix: String): String = if (message == null) prefix else "$prefix: $message"
    return when (error.javaClass) {
        IllegalStateException::class.java -> dart("Bad state")
        IllegalArgumentException::class.java -> dart("Invalid argument(s)")
        UnsupportedOperationException::class.java -> dart("Unsupported operation")
        NotImplementedError::class.java -> dart("UnimplementedError")
        Exception::class.java -> dart("Exception")
        else -> text
    }
}

private fun _rethrowIfCancelled(error: Throwable) {
    if (error is CancellationException) throw error
}

/** Runs [block] after the current frame (Flutter's `addPostFrameCallback`). */
private fun CoroutineScope?.postFrame(block: () -> Unit) {
    this?.launch {
        withFrameNanos {}
        block()
    }
}

/** Test tags of the panel (Dart's `ValueKey`s). */
const val createPersonalMusicPlaylistTag = "create-personal-music-playlist"
const val deleteSelectedPersonalMusicPlaylistsTag = "delete-selected-personal-music-playlists"
const val managePersonalMusicPlaylistsTag = "manage-personal-music-playlists"
const val filterPersonalMusicPlaylistsTag = "filter-personal-music-playlists"
const val mergeSelectedMusicPlaylistsTag = "merge-selected-music-playlists"
const val pinSelectedPersonalMusicPlaylistsTag = "pin-selected-personal-music-playlists"
const val selectVisiblePersonalMusicPlaylistsTag = "select-visible-personal-music-playlists"
const val personalMusicPlaylistsScrollTag = "personal-music-playlists-scroll"
const val personalMusicPlaylistItemsScrollTag = "personal-music-playlist-items-scroll"
const val loadMorePersonalPlaylistItemsTag = "load-more-personal-playlist-items"
const val personalMusicPlaylistHeaderTag = "personal-music-playlist-header"
const val backToPersonalMusicPlaylistsTag = "back-to-personal-music-playlists"
const val deleteSelectedPersonalMusicPlaylistItemsTag = "delete-selected-personal-music-playlist-items"
const val searchAddPersonalMusicPlaylistItemTag = "search-add-personal-music-playlist-item"
const val managePersonalMusicPlaylistItemsTag = "manage-personal-music-playlist-items"
const val filterPersonalMusicPlaylistItemsTag = "filter-personal-music-playlist-items"
const val addSelectedPersonalMusicPlaylistItemsTag = "add-selected-personal-music-playlist-items"
const val pinSelectedPersonalMusicPlaylistItemsTag = "pin-selected-personal-music-playlist-items"
const val personalMusicPlaylistNameInputTag = "personal-music-playlist-name-input"
const val importPersonalMusicPlaylistButtonTag = "import-personal-music-playlist-button"
const val selectedPersonalMusicPlaylistImportTag = "selected-personal-music-playlist-import"
const val clearPersonalMusicPlaylistImportTag = "clear-personal-music-playlist-import"

fun personalMusicPlaylistCardTag(id: String) = "personal-music-playlist-card-$id"

fun personalMusicPlaylistInformationTag(id: String) = "personal-music-playlist-information-$id"

fun personalMusicPlaylistControlsTag(id: String) = "personal-music-playlist-controls-$id"

fun cloneRoomMusicPlaylistTag(id: String) = "clone-room-music-playlist-$id"

fun sharePersonalMusicPlaylistTag(id: String) = "share-personal-music-playlist-$id"

fun personalMusicPlaylistItemTag(id: String) = "personal-music-playlist-item-$id"

fun personalMusicPlaylistItemSelectionNumberTag(id: String) = "personal-music-playlist-item-selection-number-$id"

fun shareMusicPlaylistItemTag(id: String) = "share-music-playlist-item-$id"

fun musicPlaylistSearchResultTag(source: String, trackId: String) = "music-playlist-search-result:$source:$trackId"

fun musicPlaylistSearchResultAddTag(source: String, trackId: String) = "music-playlist-search-result-add:$source:$trackId"

/**
 * Lists and manages one collection of music playlists: the personal
 * playlists in settings, or a room's playlists (room management).
 */
@Composable
fun MusicPlaylistsPanel(
    controller: PersonalMusicPlaylistsController,
    unavailableMessage: String,
    modifier: Modifier = Modifier,
    initialPlaylistId: String? = null,
    reloadToken: Int = 0,
    onLoadingChanged: ((Boolean) -> Unit)? = null,
    title: String = "歌单管理",
    previewApi: MusicTrackPreviewApi? = null,
    previewPlatformFactory: MusicTrackPreviewPlatformFactory? = null,
    shareApi: GangApi? = null,
    currentUser: CurrentUser? = null,
) = StatefulWidget(
    MusicPlaylistsPanelWidget(
        controller, initialPlaylistId, reloadToken, unavailableMessage, onLoadingChanged, title, previewApi,
        previewPlatformFactory, shareApi, currentUser, modifier,
    ),
) { _MusicPlaylistsPanelState() }

class MusicPlaylistsPanelWidget(
    val controller: PersonalMusicPlaylistsController,
    val initialPlaylistId: String?,
    val reloadToken: Int,
    val unavailableMessage: String,
    val onLoadingChanged: ((Boolean) -> Unit)?,
    val title: String,
    val previewApi: MusicTrackPreviewApi?,
    val previewPlatformFactory: MusicTrackPreviewPlatformFactory?,
    val shareApi: GangApi?,
    val currentUser: CurrentUser?,
    val modifier: Modifier,
)

private class _MusicPlaylistsPanelState : ViewState<MusicPlaylistsPanelWidget>() {
    private var _playlists: List<PersonalMusicPlaylist> = emptyList()
    private var _activePlaylist: PersonalMusicPlaylist? = null
    private var _items: List<PersonalMusicPlaylistItem> = emptyList()
    private var _selectedPlaylistIds: List<String> = emptyList()
    private var _selectedItemIds: Set<String> = emptySet()

    private var _playlistFilterKeyword = ""
    private var _playlistCountFilter = personalPlaylistCountAll
    private var _filterSource = ""
    private var _appliedFilterKeyword = ""
    private var _itemsPage = 1
    private var _itemTotal = 0
    private var _maxPlaylists = 50
    private var _maxPlaylistItems = 500
    private var _itemsHaveMore = false
    private var _loading = false
    private var _loadingItems = false
    private var _loadingMore = false
    private var _creating = false
    private var _deletingPlaylists = false
    private var _pinningPlaylists = false
    private var _mergingPlaylists = false
    private var _cloningPlaylistId: String? = null
    private var _sharingPlaylistId: String? = null
    private var _sharingItemId: String? = null
    private var _deletingItems = false
    private var _addingSelectedItems = false
    private var _pinningItems = false
    private var _managingPlaylists = false
    private var _managingItems = false
    private var _error: String? = null
    private val _busyPlaylistIds = LinkedHashSet<String>()
    private val _busyItemIds = LinkedHashSet<String>()
    private var _playlistLoadGeneration = 0
    private var _itemLoadGeneration = 0
    private var _previewController: MusicTrackPreviewController? = null
    private var _handledInitialPlaylistId: String? = null
    private var _scope: CoroutineScope? = null

    private val _capabilities: MusicPlaylistManagementCapabilities get() = widget.controller.capabilities

    private val _filterActive: Boolean
        get() = personalPlaylistFilterActive(keyword = _appliedFilterKeyword, source = _filterSource)

    private val _playlistFilterActive: Boolean
        get() = personalPlaylistListFilterActive(keyword = _playlistFilterKeyword, countFilter = _playlistCountFilter)

    private val _playlistManagementBusy: Boolean
        get() =
            _loading ||
                _creating ||
                _deletingPlaylists ||
                _pinningPlaylists ||
                _mergingPlaylists ||
                _cloningPlaylistId != null ||
                _sharingPlaylistId != null ||
                _busyPlaylistIds.isNotEmpty()

    private val _busy: Boolean
        get() =
            _loading ||
                _loadingItems ||
                _loadingMore ||
                _creating ||
                _deletingPlaylists ||
                _pinningPlaylists ||
                _mergingPlaylists ||
                _cloningPlaylistId != null ||
                _busyPlaylistIds.isNotEmpty() ||
                _deletingItems ||
                _addingSelectedItems ||
                _pinningItems ||
                _sharingItemId != null

    override fun initState() {
        _previewController = _createPreviewController()
        scheduleMicrotask { unawaited { _loadPlaylists() } }
    }

    override fun didUpdateWidget(oldWidget: MusicPlaylistsPanelWidget) {
        if (oldWidget.controller.api != widget.controller.api ||
            oldWidget.previewApi != widget.previewApi ||
            oldWidget.previewPlatformFactory != widget.previewPlatformFactory
        ) {
            val previous = _previewController
            _previewController = _createPreviewController()
            if (previous != null) unawaited { previous.dispose() }
        }
        if (oldWidget.reloadToken != widget.reloadToken || oldWidget.controller.api != widget.controller.api) {
            _handledInitialPlaylistId = null
            _managingPlaylists = false
            _managingItems = false
            _selectedPlaylistIds = emptyList()
            _selectedItemIds = emptySet()
            scheduleMicrotask { unawaited { _loadPlaylists() } }
        }
        if (oldWidget.initialPlaylistId != widget.initialPlaylistId) {
            _handledInitialPlaylistId = null
            scheduleMicrotask { unawaited { _loadPlaylists() } }
        }
    }

    override fun dispose() {
        _playlistLoadGeneration += 1
        _itemLoadGeneration += 1
        val preview = _previewController
        if (preview != null) unawaited { preview.dispose() }
    }

    private fun _createPreviewController(): MusicTrackPreviewController? {
        val api = widget.previewApi ?: widget.controller.api
        val factory = widget.previewPlatformFactory
        if (api !is MusicTrackPreviewApi || factory == null) return null
        return MusicTrackPreviewController(api = api, platform = factory.create())
    }

    private fun _setLoading(loading: Boolean) {
        if (!mounted || _loading == loading) return
        setState { _loading = loading }
        widget.onLoadingChanged?.invoke(loading)
    }

    private suspend fun _loadPlaylists() {
        if (!widget.controller.available) return
        val generation = ++_playlistLoadGeneration
        _setLoading(true)
        if (mounted) setState { _error = null }
        try {
            val result = widget.controller.loadPlaylists()
            if (!mounted || generation != _playlistLoadGeneration || result == null) {
                return
            }
            val activeID = _activePlaylist?.id
            var refreshedActive: PersonalMusicPlaylist? = null
            if (activeID != null) {
                refreshedActive = result.playlists.firstOrNull { playlist -> playlist.id == activeID }
            }
            if (activeID != null && refreshedActive == null) {
                _itemLoadGeneration += 1
            }
            setState {
                _playlists = result.playlists
                val playlistIDs = result.playlists.map { playlist -> playlist.id }.toSet()
                _selectedPlaylistIds = _selectedPlaylistIds.filter { id -> playlistIDs.contains(id) }
                _maxPlaylists = result.maxPlaylists
                _maxPlaylistItems = result.maxPlaylistItems
                _activePlaylist = refreshedActive
                if (activeID != null && refreshedActive == null) {
                    _items = emptyList()
                    _selectedItemIds = emptySet()
                    _managingItems = false
                    _loadingItems = false
                    _loadingMore = false
                }
            }
            if (refreshedActive != null) {
                scheduleMicrotask { unawaited { _loadItems(reset = true) } }
            } else {
                val initialId = widget.initialPlaylistId?.trim()
                if (initialId != null && initialId.isNotEmpty() && _handledInitialPlaylistId != initialId) {
                    _handledInitialPlaylistId = initialId
                    val initialPlaylist = result.playlists.firstOrNull { playlist -> playlist.id == initialId }
                    if (initialPlaylist != null) {
                        scheduleMicrotask { unawaited { _openPlaylist(initialPlaylist) } }
                    }
                }
            }
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (!mounted || generation != _playlistLoadGeneration) return
            setState { _error = _errorText(error) }
        } finally {
            if (mounted && generation == _playlistLoadGeneration) {
                _setLoading(false)
            }
        }
    }

    private suspend fun _openPlaylist(playlist: PersonalMusicPlaylist) {
        setState {
            _activePlaylist = playlist
            _items = emptyList()
            _selectedItemIds = emptySet()
            _managingItems = false
            _error = null
            _appliedFilterKeyword = ""
            _filterSource = ""
        }
        _loadItems(reset = true)
    }

    private fun _closePlaylist() {
        _itemLoadGeneration += 1
        val preview = _previewController
        if (preview != null) unawaited { preview.stop() }
        setState {
            _activePlaylist = null
            _items = emptyList()
            _selectedItemIds = emptySet()
            _managingItems = false
            _loadingItems = false
            _loadingMore = false
            _error = null
            _appliedFilterKeyword = ""
            _filterSource = ""
        }
    }

    private suspend fun _loadTrackPlaylistTargets(): List<MusicTrackPlaylistTarget> {
        if (!widget.controller.roomScoped) {
            return _playlists.map { playlist -> MusicTrackPlaylistTarget.personal(playlist) }
        }

        val roomId = widget.controller.sourceRoomId
        val targets = mutableListOf<MusicTrackPlaylistTarget>()
        if (roomId != null) {
            for (playlist in _playlists) targets.add(MusicTrackPlaylistTarget.room(playlist = playlist, roomId = roomId))
        }
        var personalError: Throwable? = null
        try {
            val page = widget.controller.loadPersonalPlaylistTargets()
            if (page != null) {
                targets.addAll(page.playlists.map { playlist -> MusicTrackPlaylistTarget.personal(playlist) })
            }
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            personalError = error
        }
        if (targets.isEmpty() && personalError != null) throw personalError
        return targets
    }

    private suspend fun _addSearchResultToPlaylistTarget(track: MusicBoxSearchResult, target: MusicTrackPlaylistTarget) {
        if (target.roomScoped) {
            if (!widget.controller.roomScoped || target.roomId != widget.controller.sourceRoomId) {
                throw IllegalStateException("目标房间歌单与当前房间不匹配")
            }
            _addSearchResultToPlaylist(track, target.playlist)
            return
        }
        if (!widget.controller.roomScoped) {
            _addSearchResultToPlaylist(track, target.playlist)
            return
        }
        widget.controller.addTrackToPersonalPlaylist(playlistId = target.playlist.id, track = track)
            ?: throw IllegalStateException("当前个人歌单不可写")
    }

    private suspend fun _addPlaylistItemToPlaylistTarget(item: PersonalMusicPlaylistItem, target: MusicTrackPlaylistTarget) =
        _addSearchResultToPlaylistTarget(
            MusicBoxSearchResult(trackId = item.trackId, name = item.title, artists = item.artists, source = item.source),
            target,
        )

    private suspend fun _addSearchResultToPlaylist(track: MusicBoxSearchResult, target: PersonalMusicPlaylist) {
        val added =
            widget.controller.addTrack(playlistId = target.id, track = track)
                ?: throw IllegalStateException("当前歌单不可写")
        if (!mounted) return

        val activeTarget = _activePlaylist?.id == target.id
        setState {
            _playlists =
                _playlists.map { playlist ->
                    if (playlist.id == target.id) playlist.copyWith(itemCount = playlist.itemCount + 1) else playlist
                }
            if (activeTarget) {
                _activePlaylist = _activePlaylist!!.copyWith(itemCount = _activePlaylist!!.itemCount + 1)
                _itemTotal += 1
                if (!_filterActive && !_itemsHaveMore) {
                    _items = _items + added
                } else {
                    scheduleMicrotask { unawaited { _loadItems(reset = true) } }
                }
            }
        }
    }

    private suspend fun _loadItems(reset: Boolean) {
        val playlist = _activePlaylist
        if (playlist == null || !widget.controller.available) return
        if (!reset && (_loadingItems || _loadingMore || !_itemsHaveMore)) return
        val generation = if (reset) ++_itemLoadGeneration else _itemLoadGeneration
        val page = if (reset) 1 else _itemsPage + 1
        setState {
            if (reset) {
                _loadingItems = true
                _loadingMore = false
                _selectedItemIds = emptySet()
            } else {
                _loadingMore = true
            }
            _error = null
        }
        try {
            val result =
                widget.controller.loadItems(
                    playlistId = playlist.id,
                    page = page,
                    keyword = _appliedFilterKeyword,
                    source = _filterSource,
                )
            if (!mounted || generation != _itemLoadGeneration || result == null || _activePlaylist?.id != playlist.id) {
                return
            }
            setState {
                _activePlaylist = result.playlist
                _items = if (reset) result.items else _items + result.items
                _itemsPage = result.page
                _itemTotal = result.total
                _itemsHaveMore = result.hasMore
            }
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (!mounted || generation != _itemLoadGeneration) return
            setState { _error = _errorText(error) }
        } finally {
            if (mounted && generation == _itemLoadGeneration) {
                setState {
                    _loadingItems = false
                    _loadingMore = false
                }
            }
        }
    }

    private suspend fun _createPlaylist() {
        if (!_capabilities.canCreatePlaylists || _busy || _playlists.size >= _maxPlaylists) {
            return
        }
        val loadImport = if (widget.controller.canImportPersonalPlaylist) widget.controller::loadImportPlaylists else null
        val draft =
            showDialog<PersonalPlaylistCreateDraft>(context) { _ ->
                _MusicPlaylistNameDialog(
                    title = "新建歌单",
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    confirmLabel = "创建",
                    confirmIcon = Icons.Filled.Add,
                    loadImportPlaylists = loadImport,
                )
            }
        if (!mounted || draft == null) return
        setState {
            _creating = true
            _error = null
        }
        try {
            widget.controller.createPlaylist(draft.name, importPlaylistId = draft.importPlaylistId)
            _loadPlaylists()
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) setState { _error = _errorText(error) }
        } finally {
            if (mounted) setState { _creating = false }
        }
    }

    private suspend fun _renamePlaylist(playlist: PersonalMusicPlaylist) {
        if (!_capabilities.canRenamePlaylists || _playlistManagementBusy) return
        val draft =
            showDialog<PersonalPlaylistCreateDraft>(context) { _ ->
                _MusicPlaylistNameDialog(
                    title = "重命名歌单",
                    icon = Icons.Outlined.Edit,
                    confirmLabel = "重命名",
                    confirmIcon = Icons.Filled.Check,
                    initialName = playlist.name,
                )
            }
        if (!mounted || draft == null || draft.name == playlist.name) return
        setState {
            _busyPlaylistIds.add(playlist.id)
            _error = null
        }
        try {
            widget.controller.renamePlaylist(playlistId = playlist.id, name = draft.name)
            _loadPlaylists()
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) setState { _error = _errorText(error) }
        } finally {
            if (mounted) setState { _busyPlaylistIds.remove(playlist.id) }
        }
    }

    private fun _togglePlaylistManageMode() {
        if (!_capabilities.canManagePlaylists || _playlistManagementBusy) return
        setState {
            _managingPlaylists = !_managingPlaylists
            _selectedPlaylistIds = emptyList()
        }
    }

    private fun _togglePlaylistSelection(playlistId: String) {
        if (_playlistManagementBusy) return
        setState { _selectedPlaylistIds = toggledPersonalPlaylistListSelection(_selectedPlaylistIds, playlistId) }
    }

    private fun _toggleSelectVisiblePlaylists(visiblePlaylists: List<PersonalMusicPlaylist>) {
        if (_playlistManagementBusy || visiblePlaylists.isEmpty()) return
        setState {
            _selectedPlaylistIds =
                toggledVisiblePersonalPlaylistSelection(selectedPlaylistIds = _selectedPlaylistIds, visiblePlaylists = visiblePlaylists)
        }
    }

    private suspend fun _openPlaylistFilter() {
        if (_playlistManagementBusy) return
        val draft =
            showDialog<PersonalPlaylistFilterDraft>(context) { _ ->
                _MusicPlaylistFilterDialog(keyword = _playlistFilterKeyword, countFilter = _playlistCountFilter)
            }
        if (!mounted || draft == null) return
        setState {
            _playlistFilterKeyword = draft.keyword
            _playlistCountFilter = draft.countFilter
            _selectedPlaylistIds = emptyList()
        }
    }

    private suspend fun _deleteSelectedPlaylists() {
        if (!_capabilities.canDeletePlaylists || _playlistManagementBusy || _selectedPlaylistIds.isEmpty()) {
            return
        }
        val selectedIDs = _selectedPlaylistIds.toList()
        val confirmed =
            showDialog<Boolean>(context) { _ ->
                StickerConfirmDialog(
                    title = "删除歌单",
                    body = "确定删除选中的 ${selectedIDs.size} 个歌单吗？歌单中的歌曲记录也会一并删除。",
                    confirmLabel = "删除",
                    confirmIcon = Icons.Filled.DeleteOutline,
                    danger = true,
                )
            }
        if (!mounted || confirmed != true) return
        setState {
            _deletingPlaylists = true
            _error = null
        }
        val failedIDs = mutableListOf<String>()
        var firstError: Throwable? = null
        for (playlistId in selectedIDs) {
            try {
                widget.controller.deletePlaylist(playlistId)
            } catch (error: Exception) {
                _rethrowIfCancelled(error)
                failedIDs.add(playlistId)
                if (firstError == null) firstError = error
            }
        }
        if (!mounted) return
        _loadPlaylists()
        if (!mounted) return
        setState {
            _deletingPlaylists = false
            if (failedIDs.isEmpty()) {
                _managingPlaylists = false
                _selectedPlaylistIds = emptyList()
                _error = null
            } else {
                val currentIDs = _playlists.map { playlist -> playlist.id }.toSet()
                _selectedPlaylistIds = failedIDs.filter { id -> currentIDs.contains(id) }
                val deletedCount = selectedIDs.size - failedIDs.size
                _error = "已删除 $deletedCount 个歌单，${failedIDs.size} 个删除失败：${_errorText(firstError!!)}"
            }
        }
    }

    private suspend fun _pinSelectedPlaylists() {
        if (!_capabilities.canReorderPlaylists ||
            _playlistManagementBusy ||
            personalPlaylistOrderWithSelectionPinnedToFront(playlists = _playlists, selectedPlaylistIds = _selectedPlaylistIds) == null
        ) {
            return
        }
        val selectedIDs = _selectedPlaylistIds.toList()
        setState {
            _pinningPlaylists = true
            _error = null
        }
        try {
            widget.controller.pinPlaylists(selectedIDs)
            _loadPlaylists()
            if (mounted) setState { _selectedPlaylistIds = emptyList() }
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) setState { _error = _errorText(error) }
        } finally {
            if (mounted) setState { _pinningPlaylists = false }
        }
    }

    private suspend fun _mergeSelectedPlaylists() {
        if (!widget.controller.canMergePlaylists || _playlistManagementBusy || _selectedPlaylistIds.size < 2) {
            return
        }
        val selectedIDs = _selectedPlaylistIds.toList()
        val draft =
            showDialog<PersonalPlaylistCreateDraft>(context) { _ ->
                _MusicPlaylistNameDialog(
                    title = "合并歌单",
                    icon = Icons.Filled.MergeType,
                    confirmLabel = "合并",
                    confirmIcon = Icons.Filled.MergeType,
                    description = "歌曲会按选择顺序合并并按具体链接去重。已完整合并的来源歌单会删除；超过 500 首时，未合并的剩余歌曲仍保留在原歌单中。",
                )
            }
        if (!mounted || draft == null) return
        val sourceItemCount =
            _playlists.filter { playlist -> selectedIDs.contains(playlist.id) }.fold(0) { total, playlist -> total + playlist.itemCount }
        val exceedsItemLimit = sourceItemCount > _maxPlaylistItems
        val confirmed =
            showDialog<Boolean>(context) { _ ->
                StickerConfirmDialog(
                    title = if (exceedsItemLimit) "歌曲不能全部保留" else "确认合并歌单",
                    body =
                        if (exceedsItemLimit) {
                            "所选歌单共有 $sourceItemCount 首歌曲，超过单个歌单 $_maxPlaylistItems 首上限，" +
                                "无法全部原样保留。系统会按选择顺序、按具体链接去重，" +
                                "合并后的歌单最多保留前 $_maxPlaylistItems 首；未能合并的剩余歌曲会保留在原歌单中，" +
                                "已完整合并的来源歌单会删除。是否继续？"
                        } else {
                            "确定将 ${selectedIDs.size} 个歌单按选择顺序合并为“${draft.name}”吗？" +
                                "已完整合并的来源歌单会删除，此操作无法撤销。"
                        },
                    confirmLabel = if (exceedsItemLimit) "继续合并" else "确认合并",
                    confirmIcon = Icons.Filled.MergeType,
                )
            }
        if (!mounted || confirmed != true) return
        setState {
            _mergingPlaylists = true
            _error = null
        }
        try {
            val result = widget.controller.mergePlaylists(name = draft.name, playlistIds = selectedIDs)
            if (!mounted || result == null) return
            _loadPlaylists()
            if (!mounted) return
            setState {
                _managingPlaylists = false
                _selectedPlaylistIds = emptyList()
            }
            _showPlaylistMergeResultNotice(result)
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) {
                showFloatingErrorNotice(context, "合并歌单失败：${_errorText(error)}")
            }
        } finally {
            if (mounted) setState { _mergingPlaylists = false }
        }
    }

    private fun _showPlaylistMergeResultNotice(result: PersonalMusicPlaylistMergeResult) {
        _scope.postFrame {
            if (!mounted) return@postFrame
            if (result.truncated) {
                showFloatingSuccessNotice(
                    context,
                    "已合并为“${result.playlist.name}”，按顺序保留前 ${result.itemCount} 首；其余歌曲已保留在原歌单中",
                )
                return@postFrame
            }
            val duplicateLabel = if (result.duplicateCount == 0) "" else "，已去重 ${result.duplicateCount} 首"
            showFloatingSuccessNotice(context, "已合并为“${result.playlist.name}”，共 ${result.itemCount} 首$duplicateLabel")
        }
    }

    private suspend fun _cloneRoomPlaylist(playlist: PersonalMusicPlaylist) {
        if (!widget.controller.canCloneRoomPlaylistsToPersonal || _playlistManagementBusy) {
            return
        }
        val confirmed =
            showDialog<Boolean>(context) { _ ->
                StickerConfirmDialog(
                    title = "克隆歌单",
                    body = "确定将“${playlist.name}”克隆到“我的歌单”吗？克隆后的名称使用“房间备注名 · 歌单名”。",
                    confirmLabel = "克隆",
                    confirmIcon = Icons.Outlined.LibraryAdd,
                )
            }
        if (!mounted || confirmed != true) return
        setState {
            _cloningPlaylistId = playlist.id
            _error = null
        }
        try {
            val result = widget.controller.cloneRoomPlaylistToPersonal(playlist.id)
            if (!mounted || result == null) return
            setState { _cloningPlaylistId = null }
            _showRoomPlaylistCloneResultNotice(result)
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (!mounted) return
            setState { _cloningPlaylistId = null }
            _showRoomPlaylistCloneErrorNotice(error)
        } finally {
            if (mounted && _cloningPlaylistId == playlist.id) {
                setState { _cloningPlaylistId = null }
            }
        }
    }

    private fun _showRoomPlaylistCloneResultNotice(result: PersonalMusicPlaylist) {
        // Wait until the confirmation route has finished closing and management
        // mode has rebuilt. This keeps the global notice above the settled page
        // instead of briefly placing it behind the outgoing dialog overlay.
        _scope.postFrame {
            if (!mounted) return@postFrame
            showFloatingSuccessNotice(context, "已克隆到我的歌单 - ${result.name}")
        }
    }

    private fun _showRoomPlaylistCloneErrorNotice(error: Throwable) {
        _scope.postFrame {
            if (!mounted) return@postFrame
            val message =
                if (error is ApiException && error.code == "playlist_limit_reached") {
                    "克隆失败：我的歌单已达 50 个上限"
                } else {
                    "克隆失败：${_errorText(error)}"
                }
            showFloatingErrorNotice(context, message)
        }
    }

    private suspend fun _sharePersonalPlaylist(playlist: PersonalMusicPlaylist) {
        val api = widget.shareApi
        if (api == null || widget.controller.roomScoped || _playlistManagementBusy) {
            return
        }
        val room =
            showDialog<RoomCard>(context) { _ ->
                _MusicPlaylistShareRoomDialog(api = api, playlistName = playlist.name, currentUser = widget.currentUser)
            }
        if (!mounted || room == null) return
        setState {
            _sharingPlaylistId = playlist.id
            _error = null
        }
        try {
            api.sendMessage(
                roomId = room.id,
                clientMessageId = newUuid(),
                body = "",
                type = "playlist",
                attachments = listOf(MessageAttachment(type = "playlist", playlistId = playlist.id)),
            )
            if (!mounted) return
            showFloatingSuccessNotice(context, "已将“${playlist.name}”分享到“${room.displayName}”")
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) showFloatingErrorNotice(context, "分享歌单失败：${_errorText(error)}")
        } finally {
            if (mounted && _sharingPlaylistId == playlist.id) {
                setState { _sharingPlaylistId = null }
            }
        }
    }

    private suspend fun _sharePlaylistItem(item: PersonalMusicPlaylistItem) {
        val api = widget.shareApi
        val playlist = _activePlaylist
        if (api == null || playlist == null || _sharingItemId != null) return
        val room =
            showDialog<RoomCard>(context) { _ ->
                _MusicPlaylistShareRoomDialog(
                    api = api,
                    playlistName = item.title,
                    currentUser = widget.currentUser,
                    dialogTitle = "分享歌曲",
                    itemLabel = "歌曲",
                    keyPrefix = "music-track-share-room",
                )
            }
        if (!mounted || room == null) return
        setState {
            _sharingItemId = item.id
            _error = null
        }
        try {
            api.sendMessage(
                roomId = room.id,
                clientMessageId = newUuid(),
                body = "",
                type = "music_track",
                attachments =
                    listOf(
                        MessageAttachment(
                            type = "music_track",
                            playlistId = playlist.id,
                            playlistScope = if (widget.controller.roomScoped) "room" else "personal",
                            sourceRoomId = widget.controller.sourceRoomId,
                            itemId = item.id,
                        ),
                    ),
            )
            if (!mounted) return
            showFloatingSuccessNotice(context, "已将“${item.title}”分享到“${room.displayName}”")
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) showFloatingErrorNotice(context, "分享歌曲失败：${_errorText(error)}")
        } finally {
            if (mounted && _sharingItemId == item.id) {
                setState { _sharingItemId = null }
            }
        }
    }

    private suspend fun _movePlaylist(playlist: PersonalMusicPlaylist, delta: Int) {
        if (!_capabilities.canReorderPlaylists || _playlistManagementBusy || _playlistFilterActive) {
            return
        }
        setState {
            _busyPlaylistIds.add(playlist.id)
            _error = null
        }
        try {
            widget.controller.movePlaylist(playlistId = playlist.id, delta = delta)
            _loadPlaylists()
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) setState { _error = _errorText(error) }
        } finally {
            if (mounted) setState { _busyPlaylistIds.remove(playlist.id) }
        }
    }

    private fun _openOrSelectPlaylist(playlist: PersonalMusicPlaylist) {
        if (_managingPlaylists) {
            _togglePlaylistSelection(playlist.id)
            return
        }
        unawaited { _openPlaylist(playlist) }
    }

    private suspend fun _openTrackSearchDialog() {
        val playlist = _activePlaylist
        if (!_capabilities.canAddItems || playlist == null || _busy) return
        showDialog<Unit>(context) { _ ->
            _MusicPlaylistTrackSearchDialog(
                controller = widget.controller,
                playlistId = playlist.id,
                previewController = _previewController,
                loadPlaylistTargets = { _loadTrackPlaylistTargets() },
                onAddToPlaylistTarget = { track, target -> _addSearchResultToPlaylistTarget(track, target) },
                onTrackAdded = { _refreshAfterTrackAdded() },
            )
        }
    }

    private suspend fun _refreshAfterTrackAdded() {
        if (!mounted || _activePlaylist == null) return
        _loadItems(reset = true)
        _reloadPlaylistSummariesWithoutClosing()
    }

    private suspend fun _reloadPlaylistSummariesWithoutClosing() {
        val result = widget.controller.loadPlaylists()
        if (!mounted || result == null) return
        val activeID = _activePlaylist?.id
        setState {
            _playlists = result.playlists
            _maxPlaylists = result.maxPlaylists
            _maxPlaylistItems = result.maxPlaylistItems
            if (activeID != null) {
                val refreshed = result.playlists.firstOrNull { playlist -> playlist.id == activeID }
                if (refreshed != null) _activePlaylist = refreshed
            }
        }
    }

    private suspend fun _openItemFilterDialog() {
        if (_busy) return
        val draft =
            showDialog<PersonalPlaylistItemFilterDraft>(context) { _ ->
                _MusicPlaylistItemFilterDialog(keyword = _appliedFilterKeyword, source = _filterSource)
            }
        if (!mounted || draft == null) return
        if (draft.keyword == _appliedFilterKeyword && draft.source == _filterSource) {
            return
        }
        setState {
            _appliedFilterKeyword = draft.keyword
            _filterSource = draft.source
            _selectedItemIds = emptySet()
        }
        _loadItems(reset = true)
    }

    private fun _toggleManagingItems() {
        if (!_capabilities.canManageItems || _busy) return
        setState {
            _managingItems = !_managingItems
            _selectedItemIds = emptySet()
        }
    }

    private fun _toggleItemSelection(itemID: String) {
        if (!_managingItems || _deletingItems || _addingSelectedItems || _pinningItems) {
            return
        }
        setState { _selectedItemIds = toggledPersonalPlaylistSelection(_selectedItemIds, itemID) }
    }

    private fun _toggleSelectVisible() {
        if (_addingSelectedItems || _deletingItems || _pinningItems) return
        val visibleIDs = _items.map { item -> item.id }.toSet()
        val allSelected = visibleIDs.isNotEmpty() && visibleIDs.all { id -> _selectedItemIds.contains(id) }
        setState {
            val next = LinkedHashSet(_selectedItemIds)
            if (allSelected) next.removeAll(visibleIDs) else next.addAll(visibleIDs)
            _selectedItemIds = next
        }
    }

    private suspend fun _deleteSelectedItems() {
        val playlist = _activePlaylist
        if (!_capabilities.canDeleteItems || playlist == null || _selectedItemIds.isEmpty() || _deletingItems || _addingSelectedItems) {
            return
        }
        val count = _selectedItemIds.size
        val confirmed =
            showDialog<Boolean>(context) { _ ->
                StickerConfirmDialog(
                    title = "批量删除歌曲",
                    body = "确定从“${playlist.name}”中删除已选择的 $count 首歌曲吗？",
                    confirmLabel = "删除",
                    confirmIcon = Icons.Filled.DeleteOutline,
                    danger = true,
                )
            }
        if (!mounted || confirmed != true) return
        setState {
            _deletingItems = true
            _error = null
        }
        try {
            widget.controller.deleteItems(playlistId = playlist.id, itemIds = _selectedItemIds.toList())
            setState {
                _selectedItemIds = emptySet()
                _managingItems = false
            }
            _loadItems(reset = true)
            _reloadPlaylistSummariesWithoutClosing()
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) setState { _error = _errorText(error) }
        } finally {
            if (mounted) setState { _deletingItems = false }
        }
    }

    private suspend fun _addSelectedItemsToPlaylist() {
        val source = _activePlaylist
        if (source == null || !widget.controller.canBatchAddItems || _selectedItemIds.isEmpty() || _addingSelectedItems) {
            return
        }
        val targets = _playlists.filter { playlist -> playlist.id != source.id }
        if (targets.isEmpty()) {
            showFloatingErrorNotice(context, "没有可添加歌曲的其他歌单")
            return
        }
        val target =
            showDialog<PersonalMusicPlaylist>(context) { _ ->
                _MusicPlaylistImportDialog(
                    playlists = targets,
                    selectedPlaylistId = null,
                    title = "选择目标歌单",
                    emptyText = "没有可添加歌曲的其他歌单",
                    pickerKey = "music-playlist-batch-add-target-picker",
                    optionKeyPrefix = "music-playlist-batch-add-target",
                )
            }
        if (!mounted || target == null) return

        val selectedIDs = _selectedItemIds.toList()
        val available = (_maxPlaylistItems - target.itemCount).coerceIn(0, _maxPlaylistItems)
        if (available == 0) {
            showFloatingErrorNotice(context, "添加失败：“${target.name}”已达 $_maxPlaylistItems 首上限")
            return
        }
        val exceedsLimit = selectedIDs.size > available
        val confirmed =
            showDialog<Boolean>(context) { _ ->
                StickerConfirmDialog(
                    title = if (exceedsLimit) "歌曲不能全部添加" else "确认添加歌曲",
                    body =
                        if (exceedsLimit) {
                            "“${target.name}”最多还能添加 $available 首，无法全部添加。" +
                                "系统会按选择顺序、按具体链接去重，最多添加到 " +
                                "$_maxPlaylistItems 首；原歌单中的歌曲不会删除。是否继续？"
                        } else {
                            "确定将已选择的 ${selectedIDs.size} 首歌曲按选择顺序添加到" +
                                "“${target.name}”吗？重复链接不会再次添加，原歌单中的歌曲不会删除。"
                        },
                    confirmLabel = if (exceedsLimit) "继续添加" else "确认添加",
                    confirmIcon = Icons.AutoMirrored.Filled.PlaylistAdd,
                )
            }
        if (!mounted || confirmed != true) return

        setState {
            _addingSelectedItems = true
            _error = null
        }
        try {
            val result =
                widget.controller.batchAddItems(sourcePlaylistId = source.id, targetPlaylistId = target.id, itemIds = selectedIDs)
            if (!mounted || result == null) return
            _reloadPlaylistSummariesWithoutClosing()
            if (!mounted) return
            setState {
                _managingItems = false
                _selectedItemIds = emptySet()
            }
            _showPlaylistBatchAddResultNotice(result)
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) showFloatingErrorNotice(context, "添加歌曲失败：${_errorText(error)}")
        } finally {
            if (mounted) setState { _addingSelectedItems = false }
        }
    }

    private fun _showPlaylistBatchAddResultNotice(result: PersonalMusicPlaylistBatchAddResult) {
        _scope.postFrame {
            if (!mounted) return@postFrame
            val targetName = result.playlist.name
            if (result.addedItemCount == 0 && result.omittedCount > 0) {
                showFloatingErrorNotice(context, "“$targetName”已达 $_maxPlaylistItems 首上限，没有歌曲被添加")
                return@postFrame
            }
            if (result.addedItemCount == 0) {
                showFloatingSuccessNotice(context, "“$targetName”已包含所选歌曲，未重复添加")
                return@postFrame
            }
            if (result.truncated) {
                showFloatingSuccessNotice(
                    context,
                    "已添加到“$targetName”，新增 ${result.addedItemCount} 首；" +
                        "目标歌单已达 $_maxPlaylistItems 首上限，${result.omittedCount} 首未添加",
                )
                return@postFrame
            }
            val skipped = result.duplicateCount + result.alreadyPresentCount
            val skippedLabel = if (skipped == 0) "" else "，已跳过 $skipped 首重复歌曲"
            showFloatingSuccessNotice(context, "已添加到“$targetName”，新增 ${result.addedItemCount} 首$skippedLabel")
        }
    }

    private suspend fun _pinSelectedItems() {
        val playlist = _activePlaylist
        if (!_capabilities.canReorderItems ||
            playlist == null ||
            _selectedItemIds.isEmpty() ||
            _pinningItems ||
            _filterActive ||
            personalPlaylistItemOrderWithSelectionPinnedToFront(items = _items, selectedItemIds = _selectedItemIds) == null
        ) {
            return
        }
        val selectedIds = _selectedItemIds.toList()
        setState {
            _pinningItems = true
            _error = null
        }
        try {
            widget.controller.pinItems(playlistId = playlist.id, selectedItemIds = selectedIds)
            _loadItems(reset = true)
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) setState { _error = _errorText(error) }
        } finally {
            if (mounted) setState { _pinningItems = false }
        }
    }

    private suspend fun _moveItem(item: PersonalMusicPlaylistItem, delta: Int) {
        val playlist = _activePlaylist
        if (!_capabilities.canReorderItems || playlist == null || _filterActive || _busyItemIds.contains(item.id)) {
            return
        }
        setState {
            _busyItemIds.add(item.id)
            _error = null
        }
        try {
            widget.controller.moveItem(playlistId = playlist.id, itemId = item.id, delta = delta)
            _loadItems(reset = true)
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) setState { _error = _errorText(error) }
        } finally {
            if (mounted) setState { _busyItemIds.remove(item.id) }
        }
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        if (!widget.controller.available) {
            SettingsList(modifier = widget.modifier, children = listOf { _SettingsEmptyState(text = widget.unavailableMessage) })
            return
        }
        val playlist = _activePlaylist
        if (playlist == null) _buildPlaylistList() else _buildPlaylistManager(playlist)
    }

    @Composable
    private fun _buildPlaylistList() {
        val visiblePlaylists =
            filteredPersonalMusicPlaylists(_playlists, keyword = _playlistFilterKeyword, countFilter = _playlistCountFilter)
        val selectionNumbers = personalPlaylistSelectionNumbers(_selectedPlaylistIds)
        val allVisibleSelected =
            personalPlaylistAllVisibleSelected(selectedPlaylistIds = _selectedPlaylistIds, visiblePlaylists = visiblePlaylists)
        val canPinSelection =
            personalPlaylistOrderWithSelectionPinnedToFront(playlists = _playlists, selectedPlaylistIds = _selectedPlaylistIds) != null
        val body: @Composable () -> Unit =
            when {
                _loading && _playlists.isEmpty() -> {
                    { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { _SettingsProgressIndicator() } }
                }
                _playlists.isEmpty() -> {
                    { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { _SettingsEmptyState(text = "暂无歌单，点击上方按钮新建") } }
                }
                visiblePlaylists.isEmpty() -> {
                    { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { _SettingsEmptyState(text = "没有符合筛选条件的歌单") } }
                }
                else -> {
                    {
                        _MusicPlaylistSummaryList(
                            playlists = visiblePlaylists,
                            managing = _managingPlaylists,
                            selectionNumbers = selectionNumbers,
                            busy = _managingPlaylists && _playlistManagementBusy,
                            filterActive = _playlistFilterActive,
                            onTap = ::_openOrSelectPlaylist,
                            onRename = { playlist -> unawaited { _renamePlaylist(playlist) } },
                            onMove = { playlist, direction -> unawaited { _movePlaylist(playlist, direction) } },
                            useCloneAction = widget.controller.roomScoped,
                            cloningPlaylistId = _cloningPlaylistId,
                            canShare = widget.shareApi != null && !widget.controller.roomScoped,
                            sharingPlaylistId = _sharingPlaylistId,
                            onShare = { playlist -> unawaited { _sharePersonalPlaylist(playlist) } },
                            onClone =
                                if (widget.controller.canCloneRoomPlaylistsToPersonal) {
                                    { playlist -> unawaited { _cloneRoomPlaylist(playlist) } }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }
        val managing = _managingPlaylists
        val busy = _playlistManagementBusy
        val actions = mutableListOf<StickerActionGridEntry>()
        actions.add(
            StickerActionGridEntry(
                label = if (managing) "删除" else "新建歌单",
                button =
                    ButtonWidget(
                        modifier = Modifier.testTag(if (managing) deleteSelectedPersonalMusicPlaylistsTag else createPersonalMusicPlaylistTag),
                        onPressed =
                            if (managing) {
                                if (_capabilities.canDeletePlaylists && _selectedPlaylistIds.isNotEmpty() && !busy) {
                                    { unawaited { _deleteSelectedPlaylists() } }
                                } else {
                                    null
                                }
                            } else {
                                if (_capabilities.canCreatePlaylists && !busy && _playlists.size < _maxPlaylists) {
                                    { unawaited { _createPlaylist() } }
                                } else {
                                    null
                                }
                            },
                        loading = if (managing) _deletingPlaylists else _creating,
                        tone = if (managing) ButtonTone.danger else ButtonTone.primary,
                        icon = { Icon(if (managing) Icons.Filled.DeleteOutline else Icons.AutoMirrored.Filled.PlaylistAdd) },
                        width = Dp.Infinity,
                        child = { Text(if (managing) "删除" else "新建歌单") },
                    ),
            ),
        )
        actions.add(
            StickerActionGridEntry(
                label = if (managing) "取消管理" else "管理",
                button =
                    ButtonWidget(
                        modifier = Modifier.testTag(managePersonalMusicPlaylistsTag),
                        onPressed = if (busy || !_capabilities.canManagePlaylists) null else ({ _togglePlaylistManageMode() }),
                        selected = managing,
                        tone = if (managing) ButtonTone.primary else ButtonTone.neutral,
                        icon = { Icon(if (managing) Icons.Filled.Close else Icons.Filled.ChecklistRtl) },
                        width = Dp.Infinity,
                        child = { Text(if (managing) "取消管理" else "管理") },
                    ),
            ),
        )
        actions.add(
            StickerActionGridEntry(
                label = "筛选",
                button =
                    ButtonWidget(
                        modifier = Modifier.testTag(filterPersonalMusicPlaylistsTag),
                        onPressed = if (busy) null else ({ unawaited { _openPlaylistFilter() } }),
                        selected = _playlistFilterActive,
                        icon = { Icon(Icons.Outlined.FilterAlt) },
                        width = Dp.Infinity,
                        child = { Text("筛选") },
                    ),
            ),
        )
        if (managing && widget.controller.canMergePlaylists) {
            actions.add(
                StickerActionGridEntry(
                    label = "合并",
                    button =
                        ButtonWidget(
                            modifier = Modifier.testTag(mergeSelectedMusicPlaylistsTag),
                            onPressed = if (!busy && _selectedPlaylistIds.size >= 2) ({ unawaited { _mergeSelectedPlaylists() } }) else null,
                            loading = _mergingPlaylists,
                            icon = { Icon(Icons.Filled.MergeType) },
                            width = Dp.Infinity,
                            child = { Text("合并") },
                        ),
                ),
            )
        }
        if (managing) {
            actions.add(
                StickerActionGridEntry(
                    label = "置顶",
                    button =
                        ButtonWidget(
                            modifier = Modifier.testTag(pinSelectedPersonalMusicPlaylistsTag),
                            onPressed =
                                if (!busy && canPinSelection && _capabilities.canReorderPlaylists) {
                                    { unawaited { _pinSelectedPlaylists() } }
                                } else {
                                    null
                                },
                            loading = _pinningPlaylists,
                            icon = { Icon(Icons.Filled.VerticalAlignTop) },
                            width = Dp.Infinity,
                            child = { Text("置顶") },
                        ),
                ),
            )
            actions.add(
                StickerActionGridEntry(
                    label = if (allVisibleSelected) "取消全选" else "全选",
                    button =
                        ButtonWidget(
                            modifier = Modifier.testTag(selectVisiblePersonalMusicPlaylistsTag),
                            onPressed =
                                if (!busy && visiblePlaylists.isNotEmpty()) ({ _toggleSelectVisiblePlaylists(visiblePlaylists) }) else null,
                            selected = allVisibleSelected,
                            icon = { Icon(if (allVisibleSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank) },
                            width = Dp.Infinity,
                            child = { Text(if (allVisibleSelected) "取消全选" else "全选") },
                        ),
                ),
            )
        }
        val headerChildren = mutableListOf<@Composable () -> Unit>({ StickerActionGrid(actions = actions) })
        val error = _error
        if (error != null) headerChildren.add { _MusicPlaylistErrorText(error) }
        SettingsFixedHeaderCard(
            modifier = widget.modifier,
            title = widget.title,
            spacing = 10.dp,
            trailing = {
                Text(
                    if (_playlistFilterActive) "筛选 ${visiblePlaylists.size} / ${_playlists.size}" else "${_playlists.size} / $_maxPlaylists",
                    style = uiTextStyle(color = _textMuted, fontSize = 12.0, fontWeight = FontWeight.W600),
                )
            },
            headerChildren = headerChildren,
            body = body,
        )
    }

    @Composable
    private fun _buildPlaylistItemsScroller(selectionNumbers: Map<String, Int>) {
        if (_loadingItems && _items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { _SettingsProgressIndicator() }
            return
        }
        if (_items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                _SettingsEmptyState(text = if (_filterActive) "没有匹配的歌曲" else "歌单还是空的")
            }
            return
        }

        val items = _items
        val itemCount = items.size + if (_itemsHaveMore) 1 else 0
        val previewController = _previewController
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(personalMusicPlaylistItemsScrollTag),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(itemCount, key = { index -> if (index == items.size) "load-more" else items[index].id }) { index ->
                if (index == items.size) {
                    Button(
                        modifier = Modifier.testTag(loadMorePersonalPlaylistItemsTag),
                        onPressed = if (_loadingMore) null else ({ unawaited { _loadItems(reset = false) } }),
                        loading = _loadingMore,
                        icon = { Icon(Icons.Filled.ExpandMore) },
                        width = Dp.Infinity,
                    ) {
                        Text("加载更多")
                    }
                    return@items
                }
                val item = items[index]
                _MusicPlaylistItemTile(
                    modifier = Modifier.testTag(personalMusicPlaylistItemTag(item.id)),
                    item = item,
                    managing = _managingItems,
                    selectionNumber = selectionNumbers[item.id],
                    busy =
                        _pinningItems || _deletingItems || _addingSelectedItems || _sharingItemId == item.id ||
                            _busyItemIds.contains(item.id),
                    canMoveUp = !_filterActive && index > 0,
                    canMoveDown = !_filterActive && (index < items.size - 1 || _itemsHaveMore),
                    onTap = { _toggleItemSelection(item.id) },
                    onShare = if (widget.shareApi == null) null else ({ unawaited { _sharePlaylistItem(item) } }),
                    onMoveUp = { unawaited { _moveItem(item, -1) } },
                    onMoveDown = { unawaited { _moveItem(item, 1) } },
                    previewController = previewController,
                    loadPlaylistTargets = { _loadTrackPlaylistTargets() },
                    onAddToPlaylistTarget =
                        if (_capabilities.canAddItems) {
                            { target -> _addPlaylistItemToPlaylistTarget(item, target) }
                        } else {
                            null
                        },
                )
            }
        }
    }

    @Composable
    private fun _buildPlaylistManager(playlist: PersonalMusicPlaylist) {
        val itemSelectionNumbers = personalPlaylistSelectionNumbers(_selectedItemIds.toList())
        val allVisibleSelected = _items.isNotEmpty() && _items.all { item -> _selectedItemIds.contains(item.id) }
        val canPinSelectedItems =
            !_filterActive &&
                personalPlaylistItemOrderWithSelectionPinnedToFront(items = _items, selectedItemIds = _selectedItemIds) != null
        val managing = _managingItems
        val busy = _busy
        val actions = mutableListOf<StickerActionGridEntry>()
        actions.add(
            StickerActionGridEntry(
                label = if (managing) "删除" else "搜索添加",
                button =
                    ButtonWidget(
                        modifier =
                            Modifier.testTag(if (managing) deleteSelectedPersonalMusicPlaylistItemsTag else searchAddPersonalMusicPlaylistItemTag),
                        onPressed =
                            if (managing) {
                                if (_capabilities.canDeleteItems && !_deletingItems && !_addingSelectedItems && _selectedItemIds.isNotEmpty()) {
                                    { unawaited { _deleteSelectedItems() } }
                                } else {
                                    null
                                }
                            } else {
                                if (busy || !_capabilities.canAddItems) null else ({ unawaited { _openTrackSearchDialog() } })
                            },
                        loading = if (managing) _deletingItems else false,
                        tone = if (managing) ButtonTone.danger else ButtonTone.primary,
                        icon = { Icon(if (managing) Icons.Filled.DeleteOutline else Icons.AutoMirrored.Filled.PlaylistAdd) },
                        width = Dp.Infinity,
                        child = { Text(if (managing) "删除" else "搜索添加") },
                    ),
            ),
        )
        actions.add(
            StickerActionGridEntry(
                label = if (managing) "取消管理" else "管理",
                button =
                    ButtonWidget(
                        modifier = Modifier.testTag(managePersonalMusicPlaylistItemsTag),
                        onPressed = if (busy || !_capabilities.canManageItems) null else ({ _toggleManagingItems() }),
                        selected = managing,
                        tone = if (managing) ButtonTone.primary else ButtonTone.neutral,
                        icon = { Icon(if (managing) Icons.Filled.Close else Icons.Filled.ChecklistRtl) },
                        width = Dp.Infinity,
                        child = { Text(if (managing) "取消管理" else "管理") },
                    ),
            ),
        )
        actions.add(
            StickerActionGridEntry(
                label = "筛选",
                button =
                    ButtonWidget(
                        modifier = Modifier.testTag(filterPersonalMusicPlaylistItemsTag),
                        onPressed = if (busy) null else ({ unawaited { _openItemFilterDialog() } }),
                        selected = _filterActive,
                        icon = { Icon(Icons.Outlined.FilterAlt) },
                        width = Dp.Infinity,
                        child = { Text("筛选") },
                    ),
            ),
        )
        if (managing) {
            actions.add(
                StickerActionGridEntry(
                    label = "添加到歌单",
                    button =
                        ButtonWidget(
                            modifier = Modifier.testTag(addSelectedPersonalMusicPlaylistItemsTag),
                            onPressed =
                                if (widget.controller.canBatchAddItems && !_addingSelectedItems && _selectedItemIds.isNotEmpty()) {
                                    { unawaited { _addSelectedItemsToPlaylist() } }
                                } else {
                                    null
                                },
                            loading = _addingSelectedItems,
                            icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd) },
                            width = Dp.Infinity,
                            child = { Text("添加到歌单") },
                        ),
                ),
            )
            actions.add(
                StickerActionGridEntry(
                    label = "置顶",
                    button =
                        ButtonWidget(
                            modifier = Modifier.testTag(pinSelectedPersonalMusicPlaylistItemsTag),
                            onPressed =
                                if (!_pinningItems && canPinSelectedItems && _capabilities.canReorderItems) {
                                    { unawaited { _pinSelectedItems() } }
                                } else {
                                    null
                                },
                            loading = _pinningItems,
                            icon = { Icon(Icons.Filled.VerticalAlignTop) },
                            width = Dp.Infinity,
                            child = { Text("置顶") },
                        ),
                ),
            )
            actions.add(
                StickerActionGridEntry(
                    label = if (allVisibleSelected) "取消全选" else "全选已加载",
                    button =
                        ButtonWidget(
                            onPressed = if (_items.isEmpty()) null else ({ _toggleSelectVisible() }),
                            selected = allVisibleSelected,
                            icon = { Icon(if (allVisibleSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank) },
                            width = Dp.Infinity,
                            child = { Text(if (allVisibleSelected) "取消全选" else "全选已加载") },
                        ),
                ),
            )
        }
        val headerChildren = mutableListOf<@Composable () -> Unit>({ StickerActionGrid(actions = actions) })
        if (_filterActive) {
            headerChildren.add {
                Text("筛选状态下暂不提供排序，清除筛选后可调整顺序。", style = uiTextStyle(color = _textMuted, fontSize = 12.0))
            }
        }
        val error = _error
        if (error != null) headerChildren.add { _MusicPlaylistErrorText(error) }
        SettingsFixedHeaderCard(
            modifier = widget.modifier,
            title = playlist.name,
            titleWidget = {
                Row(Modifier.fillMaxWidth().testTag(personalMusicPlaylistHeaderTag), verticalAlignment = Alignment.CenterVertically) {
                    ButtonIconPlain(
                        modifier = Modifier.testTag(backToPersonalMusicPlaylistsTag),
                        tooltip = "返回歌单列表",
                        onPressed = if (busy) null else ::_closePlaylist,
                        icon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.AutoMirrored.Outlined.QueueMusic, color = _cyan, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        playlist.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = uiTextStyle(color = _textPrimary, fontSize = 15.0, fontWeight = FontWeight.W600),
                    )
                }
            },
            spacing = 10.dp,
            trailing = {
                Text(
                    if (_filterActive) "筛选 $_itemTotal / ${playlist.itemCount} 首" else "${playlist.itemCount} / $_maxPlaylistItems 首",
                    style = uiTextStyle(color = _textMuted, fontSize = 12.0, fontWeight = FontWeight.W600),
                )
            },
            headerChildren = headerChildren,
            body = { _buildPlaylistItemsScroller(selectionNumbers = itemSelectionNumbers) },
        )
    }
}

@Composable
private fun _MusicPlaylistSummaryList(
    playlists: List<PersonalMusicPlaylist>,
    managing: Boolean,
    selectionNumbers: Map<String, Int>,
    busy: Boolean,
    filterActive: Boolean,
    onTap: (PersonalMusicPlaylist) -> Unit,
    onRename: (PersonalMusicPlaylist) -> Unit,
    onMove: (playlist: PersonalMusicPlaylist, direction: Int) -> Unit,
    useCloneAction: Boolean,
    cloningPlaylistId: String?,
    canShare: Boolean,
    sharingPlaylistId: String?,
    onShare: (PersonalMusicPlaylist) -> Unit,
    onClone: ((PersonalMusicPlaylist) -> Unit)?,
) {
    val context = rememberBuildContext()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val subPanelHorizontalPadding = 28.dp
        val contentWidth =
            if (constraints.hasBoundedWidth) (maxWidth - subPanelHorizontalPadding).coerceAtLeast(0.dp) else Dp.Infinity
        val stackControls =
            managing &&
                playlists.any { playlist ->
                    _playlistNameNeedsStackedControls(
                        context = context,
                        availableWidth = contentWidth,
                        name = playlist.name,
                        selected = selectionNumbers.containsKey(playlist.id),
                    )
                }
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(personalMusicPlaylistsScrollTag),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(playlists.size, key = { index -> playlists[index].id }) { index ->
                val playlist = playlists[index]
                _MusicPlaylistSummaryTile(
                    playlist = playlist,
                    managing = managing,
                    selectionNumber = selectionNumbers[playlist.id],
                    busy = busy,
                    canMoveUp = !filterActive && index > 0,
                    canMoveDown = !filterActive && index < playlists.size - 1,
                    stackControls = stackControls,
                    nameMaxLines =
                        if (_playlistNameNeedsTwoLines(
                                context = context,
                                availableWidth = contentWidth,
                                name = playlist.name,
                                managing = managing,
                                selected = selectionNumbers.containsKey(playlist.id),
                                controlsStacked = stackControls,
                            )
                        ) {
                            2
                        } else {
                            1
                        },
                    onTap = { onTap(playlist) },
                    onRename = { onRename(playlist) },
                    onMoveUp = { onMove(playlist, -1) },
                    onMoveDown = { onMove(playlist, 1) },
                    useCloneAction = useCloneAction,
                    cloning = cloningPlaylistId == playlist.id,
                    onClone = if (onClone == null) null else ({ onClone(playlist) }),
                    canShare = canShare,
                    sharing = sharingPlaylistId == playlist.id,
                    onShare = { onShare(playlist) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun _MusicPlaylistSummaryTile(
    playlist: PersonalMusicPlaylist,
    managing: Boolean,
    selectionNumber: Int?,
    busy: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    stackControls: Boolean,
    nameMaxLines: Int,
    onTap: () -> Unit,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    useCloneAction: Boolean,
    cloning: Boolean,
    onClone: (() -> Unit)?,
    canShare: Boolean,
    sharing: Boolean,
    onShare: () -> Unit,
) {
    val selected = selectionNumber != null
    val information: @Composable (Modifier) -> Unit = { informationModifier ->
        Row(
            informationModifier.testTag(personalMusicPlaylistInformationTag(playlist.id)).padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (managing) {
                Icon(
                    if (selected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    color = if (selected) _cyan else _textMuted,
                    size = 20.dp,
                )
                Spacer(Modifier.width(10.dp))
            }
            Icon(Icons.AutoMirrored.Filled.QueueMusic, color = _cyan, size = 24.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, maxLines = nameMaxLines, overflow = TextOverflow.Ellipsis, style = _playlistSummaryNameStyle)
                Spacer(Modifier.height(3.dp))
                Text("${playlist.itemCount} 首歌曲", style = uiTextStyle(color = _textMuted, fontSize = 12.0))
            }
            if (managing && selected) {
                _SelectionNumberBadge(selectionNumber!!)
            } else if (!managing) {
                Icon(Icons.Filled.ChevronRight, color = _textMuted, size = 22.dp)
            }
        }
    }
    val controls: @Composable () -> Unit = {
        _ManagementCardActionArea {
            FlowRow(
                Modifier.testTag(personalMusicPlaylistControlsTag(playlist.id)),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ButtonIcon(tooltip = "重命名", onPressed = if (busy) null else onRename, icon = { Icon(Icons.Outlined.Edit) }, size = 36.dp)
                ButtonIcon(
                    tooltip = "上移",
                    onPressed = if (!busy && canMoveUp) onMoveUp else null,
                    icon = { Icon(Icons.Filled.ArrowUpward) },
                    size = 36.dp,
                )
                ButtonIcon(
                    tooltip = "下移",
                    onPressed = if (!busy && canMoveDown) onMoveDown else null,
                    icon = { Icon(Icons.Filled.ArrowDownward) },
                    size = 36.dp,
                )
                if (useCloneAction) {
                    ButtonIcon(
                        modifier = Modifier.testTag(cloneRoomMusicPlaylistTag(playlist.id)),
                        tooltip = "克隆",
                        onPressed = if (busy || cloning) null else onClone,
                        loading = cloning,
                        icon = { Icon(Icons.Outlined.LibraryAdd) },
                        size = 36.dp,
                    )
                } else {
                    ButtonIcon(
                        modifier = Modifier.testTag(sharePersonalMusicPlaylistTag(playlist.id)),
                        tooltip = "分享",
                        onPressed = if (busy || !canShare || sharing) null else onShare,
                        loading = sharing,
                        icon = { Icon(Icons.Outlined.Share) },
                        size = 36.dp,
                    )
                }
            }
        }
    }
    val panel: @Composable () -> Unit = {
        _SettingsSubPanel(
            modifier = Modifier.testTag(personalMusicPlaylistCardTag(playlist.id)),
            hoverable = true,
            highlighted = selected,
        ) {
            if (stackControls && managing) {
                Column(Modifier.fillMaxWidth()) {
                    information(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { controls() }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    information(Modifier.weight(1f))
                    if (managing) {
                        Spacer(Modifier.width(10.dp))
                        controls()
                    }
                }
            }
        }
    }
    if (managing) {
        _ManagementCardTapTarget(onTap = if (busy) null else onTap) { panel() }
        return
    }
    UiPointerTapRegion(onTap = if (busy) null else onTap, disableSelection = true) { panel() }
}

private val _playlistSummaryNameStyle = uiTextStyle(color = _textPrimary, fontSize = 14.0, fontWeight = FontWeight.W600)
private val _playlistSummaryControlsWidth = 36.dp * 4 + 8.dp * 3
private val _playlistSummaryControlsGap = 10.dp

private fun _playlistNameNeedsStackedControls(
    context: BuildContext,
    availableWidth: Dp,
    name: String,
    selected: Boolean,
): Boolean {
    val requiredWidth =
        _playlistSummaryInformationFixedWidth(managing = true, selected = selected) +
            _playlistSummaryTextWidth(context, name) +
            _playlistSummaryTruncationGuardWidth(context) +
            _playlistSummaryControlsGap +
            _playlistSummaryControlsWidth
    return requiredWidth > availableWidth
}

private fun _playlistNameNeedsTwoLines(
    context: BuildContext,
    availableWidth: Dp,
    name: String,
    managing: Boolean,
    selected: Boolean,
    controlsStacked: Boolean,
): Boolean {
    if (managing && !controlsStacked) return false
    val requiredWidth =
        _playlistSummaryInformationFixedWidth(managing = managing, selected = selected) +
            _playlistSummaryTextWidth(context, name) +
            _playlistSummaryTruncationGuardWidth(context)
    return requiredWidth > availableWidth
}

private fun _playlistSummaryInformationFixedWidth(managing: Boolean, selected: Boolean): Dp =
    ((if (managing) 20 + 10 else 0) + 24 + 12 + (if (managing) (if (selected) 28 else 0) else 22)).dp

/**
 * Dart measures the name with the bare style (a `TextPainter` does not merge
 * the ambient `DefaultTextStyle`); the width is the painter's ceiled width.
 */
private fun _playlistSummaryTextWidth(context: BuildContext, text: String): Dp =
    TextPainter(text, _playlistSummaryNameStyle, maxLines = 1).layout(context).width.dp

private fun _playlistSummaryTruncationGuardWidth(context: BuildContext): Dp = _playlistSummaryTextWidth(context, "…")

@Composable
private fun _SelectionNumberBadge(number: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(28.dp).decoration(_cyan, CircleShape), contentAlignment = Alignment.Center) {
        Text("$number", style = uiTextStyle(color = _primaryDark, fontSize = 12.0, fontWeight = FontWeight.W700))
    }
}

@Composable
private fun _MusicPlaylistSearchRow(
    controller: TextEditingController,
    focusNode: FocusNode?,
    hintText: String,
    onSubmitted: (String) -> Unit,
) {
    Input(
        controller = controller,
        focusNode = focusNode,
        hintText = hintText,
        prefixIcon = Icons.Filled.Search,
        showClearButton = true,
        onSubmitted = onSubmitted,
    )
}

@Composable
private fun _MusicPlaylistSearchResultTile(
    result: MusicBoxSearchResult,
    query: String,
    loading: Boolean,
    onAdd: () -> Unit,
    previewController: MusicTrackPreviewController?,
    loadPlaylistTargets: suspend () -> List<MusicTrackPlaylistTarget>,
    onAddToPlaylistTarget: suspend (track: MusicBoxSearchResult, target: MusicTrackPlaylistTarget) -> Unit,
) {
    val panel: @Composable () -> Unit = {
        _SettingsSubPanel(modifier = Modifier.testTag(musicPlaylistSearchResultTag(result.source, result.trackId))) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, color = _cyan, size = 20.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    HighlightedText(
                        text = result.name,
                        query = query,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = uiTextStyle(color = _textPrimary, fontSize = 13.0, fontWeight = FontWeight.W600),
                    )
                    Spacer(Modifier.height(3.dp))
                    HighlightedText(
                        text = personalPlaylistArtistsLabel(result.artists),
                        query = query,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = uiTextStyle(color = _textMuted, fontSize = 12.0),
                    )
                }
                Spacer(Modifier.width(10.dp))
                _ManagementCardActionArea {
                    ButtonIcon(
                        modifier = Modifier.testTag(musicPlaylistSearchResultAddTag(result.source, result.trackId)),
                        tooltip = "添加到当前歌单",
                        onPressed = if (loading) null else onAdd,
                        loading = loading,
                        tone = ButtonTone.primary,
                        icon = { Icon(Icons.Filled.Add) },
                        size = 40.dp,
                    )
                }
            }
        }
    }
    val preview = previewController
    if (preview == null) {
        panel()
        return
    }
    MusicTrackHoverCard(
        data =
            MusicTrackCardData(
                id = "${result.source}:${result.trackId}",
                source = result.source,
                trackId = result.trackId,
                title = result.name,
                artists = result.artists,
                durationMs = 0,
            ),
        previewController = preview,
        loadPlaylistTargets = loadPlaylistTargets,
        onAddToPlaylistTarget = { target -> onAddToPlaylistTarget(result, target) },
        child = panel,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun _MusicPlaylistItemTile(
    item: PersonalMusicPlaylistItem,
    managing: Boolean,
    selectionNumber: Int?,
    busy: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTap: () -> Unit,
    onShare: (() -> Unit)?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    previewController: MusicTrackPreviewController?,
    loadPlaylistTargets: suspend () -> List<MusicTrackPlaylistTarget>,
    onAddToPlaylistTarget: (suspend (MusicTrackPlaylistTarget) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val selected = selectionNumber != null
    val information: @Composable (Modifier) -> Unit = { informationModifier ->
        Row(informationModifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (managing) {
                Icon(
                    if (selected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    size = 20.dp,
                    color = if (selected) _cyan else _textMuted,
                )
                Spacer(Modifier.width(10.dp))
            }
            Icon(Icons.Filled.MusicNote, size = 19.dp, color = _cyan)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = uiTextStyle(color = _textPrimary, fontSize = 13.0, fontWeight = FontWeight.W600),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${personalPlaylistArtistsLabel(item.artists)} · ${personalPlaylistSourceLabel(item.source)}",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = uiTextStyle(color = _textMuted, fontSize = 12.0),
                )
            }
            if (managing && selected) {
                Spacer(Modifier.width(10.dp))
                _SelectionNumberBadge(selectionNumber!!, Modifier.testTag(personalMusicPlaylistItemSelectionNumberTag(item.id)))
            }
        }
    }
    val controls: @Composable () -> Unit = {
        _ManagementCardActionArea {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ButtonIcon(
                    tooltip = "上移",
                    onPressed = if (!busy && canMoveUp) onMoveUp else null,
                    icon = { Icon(Icons.Filled.ArrowUpward) },
                    size = 36.dp,
                )
                ButtonIcon(
                    tooltip = "下移",
                    onPressed = if (!busy && canMoveDown) onMoveDown else null,
                    icon = { Icon(Icons.Filled.ArrowDownward) },
                    size = 36.dp,
                )
                if (onShare != null) {
                    ButtonIcon(
                        modifier = Modifier.testTag(shareMusicPlaylistItemTag(item.id)),
                        tooltip = "分享歌曲",
                        onPressed = if (busy) null else onShare,
                        icon = { Icon(Icons.Outlined.Share) },
                        size = 36.dp,
                        loading = busy,
                    )
                }
            }
        }
    }
    val panel: @Composable () -> Unit = {
        MusicPlaylistTrackSurface(highlighted = selected) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 420.dp) {
                    Column(Modifier.fillMaxWidth()) {
                        information(Modifier.fillMaxWidth())
                        if (managing) {
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { controls() }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        information(Modifier.weight(1f))
                        if (managing) {
                            Spacer(Modifier.width(10.dp))
                            controls()
                        }
                    }
                }
            }
        }
    }
    if (!managing) {
        val preview = previewController
        if (preview == null) {
            Box(modifier, propagateMinConstraints = true) { panel() }
            return
        }
        MusicTrackHoverCard(
            modifier = modifier,
            data =
                MusicTrackCardData(
                    id = item.id,
                    source = item.source,
                    trackId = item.trackId,
                    title = item.title,
                    artists = item.artists,
                    durationMs = item.durationMs,
                ),
            previewController = preview,
            loadPlaylistTargets = loadPlaylistTargets,
            onAddToPlaylistTarget = onAddToPlaylistTarget,
            child = panel,
        )
        return
    }
    _ManagementCardTapTarget(modifier = modifier, onTap = if (busy) null else onTap) { panel() }
}

/**
 * Makes every non-action part of a management card select the card.
 *
 * Action clusters use [_ManagementCardActionArea], which consumes its taps
 * (Dart's nested recognizer winning the gesture arena). This keeps active and
 * disabled action buttons from also toggling the card selection while
 * preserving the full card's padding and layout gaps as useful selection
 * targets.
 */
@Composable
private fun _ManagementCardTapTarget(
    onTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
    child: @Composable () -> Unit,
) {
    // Keyed on enablement only: a rebuild (which hands over a new callback)
    // must not restart the detector and drop a tap that is in flight, as
    // Flutter's recognizer survives rebuilds.
    val currentOnTap = androidx.compose.runtime.rememberUpdatedState(onTap)
    Box(
        modifier
            .semantics {
                role = Role.Button
                if (onTap == null) {
                    disabled()
                } else {
                    onClick {
                        onTap()
                        true
                    }
                }
            }
            .pointerInput(onTap != null) {
                if (currentOnTap.value == null) return@pointerInput
                detectTapGestures(onTap = { currentOnTap.value?.invoke() })
            },
        propagateMinConstraints = true,
    ) {
        child()
    }
}

@Composable
private fun _ManagementCardActionArea(child: @Composable () -> Unit) {
    Box(Modifier.pointerInput(Unit) { detectTapGestures(onTap = {}) }) { child() }
}

@Composable
private fun _MusicPlaylistErrorText(message: String) {
    Text(message, style = uiTextStyle(color = _danger, fontSize = 12.0, height = 1.4))
}

@Composable
private fun _MusicPlaylistTrackSearchDialog(
    controller: PersonalMusicPlaylistsController,
    playlistId: String,
    previewController: MusicTrackPreviewController?,
    loadPlaylistTargets: suspend () -> List<MusicTrackPlaylistTarget>,
    onAddToPlaylistTarget: suspend (track: MusicBoxSearchResult, target: MusicTrackPlaylistTarget) -> Unit,
    onTrackAdded: suspend () -> Unit,
) = StatefulWidget(
    _MusicPlaylistTrackSearchDialogWidget(
        controller, playlistId, previewController, loadPlaylistTargets, onAddToPlaylistTarget, onTrackAdded,
    ),
) { _MusicPlaylistTrackSearchDialogState() }

private class _MusicPlaylistTrackSearchDialogWidget(
    val controller: PersonalMusicPlaylistsController,
    val playlistId: String,
    val previewController: MusicTrackPreviewController?,
    val loadPlaylistTargets: suspend () -> List<MusicTrackPlaylistTarget>,
    val onAddToPlaylistTarget: suspend (track: MusicBoxSearchResult, target: MusicTrackPlaylistTarget) -> Unit,
    val onTrackAdded: suspend () -> Unit,
)

private class _MusicPlaylistTrackSearchDialogState : ViewState<_MusicPlaylistTrackSearchDialogWidget>() {
    private val _searchController = TextEditingController()
    private val _searchFocusNode = FocusNode()
    private val _busyTrackKeys = LinkedHashSet<String>()
    private var _debounce: CompositionTimer? = null
    private var _results: List<MusicBoxSearchResult> = emptyList()
    private var _source = musicBoxDefaultSource
    private var _error: String? = null
    private var _generation = 0
    private var _searching = false
    private var _scope: CoroutineScope? = null
    private var _lastText = ""

    private val _handleSearchChanged: () -> Unit = {
        // Dart's controller listener fires on every value change; the search
        // only depends on the text.
        val text = _searchController.text
        if (text != _lastText) {
            _lastText = text
            _scheduleSearch()
        }
    }

    override fun initState() {
        _searchController.addListener(_handleSearchChanged)
        _searchFocusNode.requestFocus()
    }

    override fun dispose() {
        _generation += 1
        _debounce?.cancel()
        _searchController.removeListener(_handleSearchChanged)
        _searchController.dispose()
        _searchFocusNode.dispose()
    }

    private fun _scheduleSearch(immediate: Boolean = false) {
        _debounce?.cancel()
        val keyword = _searchController.text.trim()
        val source = _source
        val generation = ++_generation
        if (keyword.isEmpty()) {
            setState {
                _results = emptyList()
                _searching = false
                _error = null
            }
            return
        }
        setState {
            _searching = true
            _results = emptyList()
            _error = null
        }
        if (immediate) {
            unawaited { _runSearch(keyword = keyword, source = source, generation = generation) }
            return
        }
        _debounce =
            _scope?.startTimer(350.milliseconds) {
                unawaited { _runSearch(keyword = keyword, source = source, generation = generation) }
            }
    }

    private suspend fun _submitSearch() {
        _debounce?.cancel()
        val keyword = _searchController.text.trim()
        if (keyword.isEmpty()) {
            _scheduleSearch(immediate = true)
            return
        }
        val generation = ++_generation
        setState {
            _searching = true
            _results = emptyList()
            _error = null
        }
        _runSearch(keyword = keyword, source = _source, generation = generation)
    }

    private suspend fun _runSearch(keyword: String, source: String, generation: Int) {
        try {
            val results = widget.controller.searchTracks(keyword = keyword, source = source)
            if (!mounted ||
                generation != _generation ||
                _searchController.text.trim() != keyword ||
                _source != source ||
                results == null
            ) {
                return
            }
            setState { _results = results }
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (!mounted || generation != _generation) return
            setState {
                _results = emptyList()
                _error = _errorText(error)
            }
        } finally {
            if (mounted && generation == _generation) {
                setState { _searching = false }
            }
        }
    }

    private fun _setSource(source: String) {
        if (_source == source) return
        _debounce?.cancel()
        _generation += 1
        setState {
            _source = source
            _results = emptyList()
            _searching = false
            _error = null
        }
        if (_searchController.text.trim().isNotEmpty()) {
            _scheduleSearch(immediate = true)
        }
    }

    private suspend fun _addTrack(track: MusicBoxSearchResult) {
        val key = "${track.source}:${track.trackId}"
        if (_busyTrackKeys.contains(key)) return
        setState {
            _busyTrackKeys.add(key)
            _error = null
        }
        try {
            widget.controller.addTrack(playlistId = widget.playlistId, track = track)
            widget.onTrackAdded()
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) setState { _error = _errorText(error) }
        } finally {
            if (mounted) setState { _busyTrackKeys.remove(key) }
        }
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        val media = MediaQuery.current
        val contentHeight = (media.size.height - media.viewInsets.bottom - 220.dp).coerceIn(180.dp, 520.dp)
        DialogFrame(
            title = "搜索添加",
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            maxWidth = 680.dp,
            adaptiveActions =
                listOf(
                    ResponsiveDialogAction(
                        label = "完成",
                        icon = Icons.Filled.Check,
                        tone = ButtonTone.primary,
                        onPressed = { Navigator.of(context).pop() },
                    ),
                ),
            child = {
                Column(Modifier.fillMaxWidth().height(contentHeight)) {
                    SegmentedControl(
                        expanded = true,
                        value = _source,
                        onChanged = ::_setSource,
                        segments = musicBoxSources.map { source -> Segment(value = source.id, label = source.label) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    _MusicPlaylistSearchRow(
                        controller = _searchController,
                        focusNode = _searchFocusNode,
                        hintText = "搜索歌曲添加到歌单",
                        onSubmitted = { unawaited { _submitSearch() } },
                    )
                    val error = _error
                    if (error != null) {
                        Spacer(Modifier.height(10.dp))
                        _MusicPlaylistErrorText(error)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (_searching && _results.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { _SettingsProgressIndicator() }
                        } else if (_results.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                _SettingsEmptyState(
                                    text = if (_searchController.text.trim().isEmpty()) "输入歌名或歌手后自动搜索" else "没有找到相关歌曲",
                                )
                            }
                        } else {
                            val results = _results
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(results.size) { index ->
                                    val result = results[index]
                                    _MusicPlaylistSearchResultTile(
                                        result = result,
                                        query = _searchController.text,
                                        loading = _busyTrackKeys.contains("${result.source}:${result.trackId}"),
                                        onAdd = { unawaited { _addTrack(result) } },
                                        previewController = widget.previewController,
                                        loadPlaylistTargets = widget.loadPlaylistTargets,
                                        onAddToPlaylistTarget = widget.onAddToPlaylistTarget,
                                    )
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
private fun _MusicPlaylistItemFilterDialog(keyword: String, source: String) =
    StatefulWidget(_MusicPlaylistItemFilterDialogWidget(keyword, source)) { _MusicPlaylistItemFilterDialogState() }

private class _MusicPlaylistItemFilterDialogWidget(val keyword: String, val source: String)

private class _MusicPlaylistItemFilterDialogState : ViewState<_MusicPlaylistItemFilterDialogWidget>() {
    private lateinit var _keywordController: TextEditingController
    private val _keywordFocusNode = FocusNode()
    private lateinit var _source: String

    override fun initState() {
        _keywordController = TextEditingController(text = widget.keyword)
        _source = widget.source
        _keywordFocusNode.requestFocus()
    }

    override fun dispose() {
        _keywordController.dispose()
        _keywordFocusNode.dispose()
    }

    @Composable
    override fun build() {
        DialogFrame(
            title = "筛选歌曲",
            icon = Icons.Outlined.FilterAlt,
            adaptiveActions =
                listOf(
                    ResponsiveDialogAction(
                        label = "重置",
                        icon = Icons.Filled.RestartAlt,
                        onPressed = {
                            _keywordController.clear()
                            setState { _source = "" }
                        },
                    ),
                    ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop() }),
                    ResponsiveDialogAction(
                        label = "确认",
                        icon = Icons.Filled.Check,
                        tone = ButtonTone.primary,
                        onPressed = {
                            Navigator.of(context).pop(
                                PersonalPlaylistItemFilterDraft(keyword = _keywordController.text.trim(), source = _source),
                            )
                        },
                    ),
                ),
            child = {
                _StretchColumn(
                    listOf(
                        { _MusicPlaylistFieldLabel("名称关键字") },
                        _gap(8.dp),
                        {
                            Input(
                                controller = _keywordController,
                                focusNode = _keywordFocusNode,
                                hintText = "",
                                showClearButton = true,
                                minLines = 1,
                                maxLines = 1,
                            )
                        },
                        _gap(16.dp),
                        { _MusicPlaylistFieldLabel("歌曲来源") },
                        _gap(8.dp),
                        {
                            SegmentedControl(
                                expanded = true,
                                value = _source,
                                onChanged = { source -> setState { _source = source } },
                                segments =
                                    listOf(Segment(value = "", label = "全部")) +
                                        musicBoxSources.map { source -> Segment(value = source.id, label = source.label) },
                            )
                        },
                    ),
                )
            },
        )
    }
}

private class _MusicPlaylistCountFilter(val value: String, val label: String)

private val _countFilters =
    listOf(
        _MusicPlaylistCountFilter(personalPlaylistCountAll, "全部"),
        _MusicPlaylistCountFilter(personalPlaylistCountEmpty, "空歌单"),
        _MusicPlaylistCountFilter(personalPlaylistCount1To10, "1～10 首"),
        _MusicPlaylistCountFilter(personalPlaylistCount11To50, "11～50 首"),
        _MusicPlaylistCountFilter(personalPlaylistCount51To100, "51～100 首"),
        _MusicPlaylistCountFilter(personalPlaylistCountOver100, "100 首以上"),
    )

@Composable
private fun _MusicPlaylistFilterDialog(keyword: String, countFilter: String) =
    StatefulWidget(_MusicPlaylistFilterDialogWidget(keyword, countFilter)) { _MusicPlaylistFilterDialogState() }

private class _MusicPlaylistFilterDialogWidget(val keyword: String, val countFilter: String)

private class _MusicPlaylistFilterDialogState : ViewState<_MusicPlaylistFilterDialogWidget>() {
    private lateinit var _keywordController: TextEditingController
    private val _keywordFocusNode = FocusNode()
    private lateinit var _countFilter: String

    override fun initState() {
        _keywordController = TextEditingController(text = widget.keyword)
        _countFilter = widget.countFilter
        _keywordFocusNode.requestFocus()
    }

    override fun dispose() {
        _keywordController.dispose()
        _keywordFocusNode.dispose()
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun build() {
        DialogFrame(
            title = "筛选",
            icon = Icons.Outlined.FilterAlt,
            adaptiveActions =
                listOf(
                    ResponsiveDialogAction(
                        label = "重置",
                        icon = Icons.Filled.RestartAlt,
                        onPressed = {
                            _keywordController.clear()
                            setState { _countFilter = personalPlaylistCountAll }
                        },
                    ),
                    ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop() }),
                    ResponsiveDialogAction(
                        label = "确认",
                        icon = Icons.Filled.Check,
                        tone = ButtonTone.primary,
                        onPressed = {
                            Navigator.of(context).pop(
                                PersonalPlaylistFilterDraft(keyword = _keywordController.text.trim(), countFilter = _countFilter),
                            )
                        },
                    ),
                ),
            child = {
                _StretchColumn(
                    listOf(
                        { _MusicPlaylistFieldLabel("名称关键字") },
                        _gap(8.dp),
                        {
                            Input(
                                controller = _keywordController,
                                focusNode = _keywordFocusNode,
                                hintText = "",
                                showClearButton = true,
                                minLines = 1,
                                maxLines = 1,
                            )
                        },
                        _gap(16.dp),
                        { _MusicPlaylistFieldLabel("歌曲数量") },
                        _gap(8.dp),
                        {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val gap = 8.dp
                                val columns = if (maxWidth < 330.dp) 2 else 3
                                val itemWidth = (maxWidth - gap * (columns - 1)) / columns.toFloat()
                                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                    for (row in _countFilters.chunked(columns)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                            for (filter in row) {
                                                val selected = _countFilter == filter.value
                                                Box(Modifier.width(itemWidth)) {
                                                    PressableSurface(
                                                        onPressed = { setState { _countFilter = filter.value } },
                                                        selected = selected,
                                                        height = 38.dp,
                                                        backgroundColor = _primaryDark,
                                                        selectedBackgroundColor = UiColors.selected,
                                                        pressedBackgroundColor = _primaryDarkLow,
                                                        borderColor = _borderColor,
                                                        selectedBorderColor = UiColors.selectedBorder,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Text(
                                                                filter.label,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                style =
                                                                    uiTextStyle(
                                                                        color = if (selected) _cyan else _textSecondary,
                                                                        fontSize = 13.0,
                                                                        fontWeight = FontWeight.W600,
                                                                    ),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    ),
                )
            },
        )
    }
}

@Composable
private fun _MusicPlaylistFieldLabel(text: String) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = uiTextStyle(color = _textMuted, fontSize = 12.0, fontWeight = FontWeight.W500),
    )
}

@Composable
private fun _MusicPlaylistShareRoomDialog(
    api: GangApi,
    playlistName: String,
    currentUser: CurrentUser?,
    dialogTitle: String = "分享歌单",
    itemLabel: String = "歌单",
    keyPrefix: String = "music-playlist-share-room",
) = StatefulWidget(_MusicPlaylistShareRoomDialogWidget(api, playlistName, currentUser, dialogTitle, itemLabel, keyPrefix)) {
    _MusicPlaylistShareRoomDialogState()
}

private class _MusicPlaylistShareRoomDialogWidget(
    val api: GangApi,
    val playlistName: String,
    val currentUser: CurrentUser?,
    val dialogTitle: String,
    val itemLabel: String,
    val keyPrefix: String,
)

private class _MusicPlaylistShareRoomDialogState : ViewState<_MusicPlaylistShareRoomDialogWidget>() {
    private val _searchController = TextEditingController()
    private val _searchFocusNode = FocusNode()
    private var _rooms: List<RoomCard> = emptyList()
    private var _selectedRoomId: String? = null
    private var _error: String? = null
    private var _loading = true

    override fun initState() {
        unawaited { _loadRooms() }
    }

    override fun dispose() {
        _searchController.dispose()
        _searchFocusNode.dispose()
    }

    private suspend fun _loadRooms() {
        setState {
            _loading = true
            _error = null
        }
        try {
            val rooms = mutableListOf<RoomCard>()
            val seen = HashSet<String>()
            val seenCursors = HashSet<String>()
            var cursor: String? = null
            do {
                val page = widget.api.listRooms(limit = 50, cursor = cursor)
                for (room in page.rooms) {
                    if (seen.add(room.id)) rooms.add(room)
                }
                val next = page.nextCursor?.trim()
                cursor = if (next == null || next.isEmpty() || !seenCursors.add(next)) null else next
            } while (cursor != null)
            if (!mounted) return
            setState {
                _rooms = rooms
                _loading = false
            }
            // Dart focuses after the frame; the node defers until attached.
            _searchFocusNode.requestFocus()
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (!mounted) return
            setState {
                _loading = false
                _error = _errorText(error)
            }
        }
    }

    private val _visibleRooms: List<RoomCard>
        get() {
            val query = _searchController.text.trim().lowercase()
            if (query.isEmpty()) return _rooms
            return _rooms.filter { room ->
                room.displayName.lowercase().contains(query) ||
                    room.name.lowercase().contains(query) ||
                    room.rid.lowercase().contains(query)
            }
        }

    private fun _confirm() {
        val selected = _rooms.firstOrNull { room -> room.id == _selectedRoomId }
        if (selected != null) Navigator.of(context).pop(selected)
    }

    private fun _roomProfile(room: RoomCard): PublicRoom =
        PublicRoom(
            id = room.id,
            rid = room.rid,
            name = room.displayName,
            avatarLabel = room.name,
            avatarUrl = room.avatarUrl,
            defaultAvatarKey = room.defaultAvatarKey,
            visibility = room.visibility,
            joinPolicy = "approval_required",
            description = room.description,
            memberCount = room.memberCount,
            onlineMemberCount = room.onlineMemberCount,
            liveParticipantCount = room.liveParticipantCount,
            joined = true,
            joinState = "joined",
        )

    private suspend fun _resolveRoomProfile(room: PublicRoom): PublicRoom {
        val detail = widget.api.getRoom(room.id)
        return publicRoomFromRoomDetail(detail)
    }

    @Composable
    override fun build() {
        val visibleRooms = _visibleRooms
        val viewportHeight = (MediaQuery.current.size.height * 0.58f).coerceIn(240.dp, 560.dp)
        val keyPrefix = widget.keyPrefix
        DialogFrame(
            title = widget.dialogTitle,
            icon = Icons.Outlined.Share,
            maxWidth = 620.dp,
            adaptiveActions =
                listOf(
                    ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop() }),
                    ResponsiveDialogAction(
                        label = "确认分享",
                        icon = Icons.AutoMirrored.Outlined.Send,
                        tone = ButtonTone.primary,
                        onPressed = if (_selectedRoomId == null) null else ::_confirm,
                    ),
                ),
            child = {
                Column(Modifier.fillMaxWidth().height(viewportHeight)) {
                    Text(
                        "选择要将${widget.itemLabel}“${widget.playlistName}”分享到的文字频道",
                        style = uiTextStyle(color = _textMuted, fontSize = 12.0),
                    )
                    Spacer(Modifier.height(10.dp))
                    Input(
                        modifier = Modifier.testTag("$keyPrefix-search"),
                        controller = _searchController,
                        focusNode = _searchFocusNode,
                        prefixIcon = Icons.Filled.Search,
                        hintText = "搜索房间",
                        showClearButton = true,
                        onChanged = { _ -> setState() },
                        textInputAction = ImeAction.Search,
                    )
                    val error = _error
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        _MusicPlaylistErrorText(error)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (_loading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { _SettingsProgressIndicator() }
                        } else if (visibleRooms.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                _SettingsEmptyState(text = if (_rooms.isEmpty()) "还没有可分享的房间" else "没有匹配的房间")
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(visibleRooms.size, key = { index -> visibleRooms[index].id }) { index ->
                                    val room = visibleRooms[index]
                                    _ShareRoomOption(room = room, keyPrefix = keyPrefix)
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    @Composable
    private fun _ShareRoomOption(room: RoomCard, keyPrefix: String) {
        val selected = room.id == _selectedRoomId
        val avatarUrl = AppConfigScope.of(context).resolveAssetUrl(room.avatarUrl)
        _ManagementCardTapTarget(
            modifier = Modifier.testTag("$keyPrefix-option-${room.id}"),
            onTap = { setState { _selectedRoomId = room.id } },
        ) {
            _SettingsSubPanel(highlighted = selected, hoverable = true) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val currentUser = widget.currentUser
                    if (currentUser != null) {
                        RoomHoverCard(
                            modifier = Modifier.testTag("$keyPrefix-profile-${room.id}"),
                            room = _roomProfile(room),
                            currentUser = currentUser,
                            onResolveRoom = ::_resolveRoomProfile,
                        ) {
                            Avatar(label = room.displayName, imageUrl = avatarUrl, defaultAvatarKey = room.defaultAvatarKey, size = 36.dp)
                        }
                    } else {
                        Avatar(label = room.displayName, imageUrl = avatarUrl, defaultAvatarKey = room.defaultAvatarKey, size = 36.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(Modifier.weight(1f).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(room.displayName, style = uiTextStyle(color = _textPrimary, fontSize = 14.0, fontWeight = FontWeight.W600))
                            if (room.rid.trim().isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text("RID：${room.rid}", style = uiTextStyle(color = _textMuted, fontSize = 12.0))
                            }
                        }
                        Icon(
                            if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                            color = if (selected) _cyan else _textMuted,
                            size = 20.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun _MusicPlaylistNameDialog(
    title: String,
    icon: ImageVector,
    confirmLabel: String,
    confirmIcon: ImageVector,
    initialName: String = "",
    loadImportPlaylists: (suspend () -> PersonalMusicPlaylistPage?)? = null,
    description: String? = null,
) = StatefulWidget(
    _MusicPlaylistNameDialogWidget(title, icon, confirmLabel, confirmIcon, initialName, loadImportPlaylists, description),
) { _MusicPlaylistNameDialogState() }

private class _MusicPlaylistNameDialogWidget(
    val title: String,
    val icon: ImageVector,
    val confirmLabel: String,
    val confirmIcon: ImageVector,
    val initialName: String,
    val loadImportPlaylists: (suspend () -> PersonalMusicPlaylistPage?)?,
    val description: String?,
)

private class _MusicPlaylistNameDialogState : ViewState<_MusicPlaylistNameDialogWidget>() {
    private lateinit var _controller: TextEditingController
    private val _focusNode = FocusNode()
    private var _importPlaylist: PersonalMusicPlaylist? = null
    private var _loadingImportPlaylists = false
    private var _error: String? = null

    override fun initState() {
        _controller = TextEditingController(text = widget.initialName)
        _focusNode.requestFocus()
        _controller.selection = TextRange(0, _controller.text.length)
    }

    override fun dispose() {
        _controller.dispose()
        _focusNode.dispose()
    }

    private fun _submit() {
        val name = normalizedPersonalPlaylistName(_controller.text)
        if (name == null) {
            setState { _error = "请输入 1～64 个字符的歌单名" }
            return
        }
        Navigator.of(context).pop(PersonalPlaylistCreateDraft(name = name, importPlaylistId = _importPlaylist?.id))
    }

    private suspend fun _chooseImportPlaylist() {
        val load = widget.loadImportPlaylists
        if (load == null || _loadingImportPlaylists) return
        FocusScope.unfocus(context)
        setState {
            _loadingImportPlaylists = true
            _error = null
        }
        try {
            val page = load()
            if (!mounted) return
            val selected =
                showDialog<PersonalMusicPlaylist>(context) { _ ->
                    _MusicPlaylistImportDialog(playlists = page?.playlists ?: emptyList(), selectedPlaylistId = _importPlaylist?.id)
                }
            if (!mounted || selected == null) return
            setState { _importPlaylist = selected }
        } catch (error: Exception) {
            _rethrowIfCancelled(error)
            if (mounted) setState { _error = _errorText(error) }
        } finally {
            if (mounted) setState { _loadingImportPlaylists = false }
        }
    }

    @Composable
    override fun build() {
        val children = mutableListOf<@Composable () -> Unit>()
        val description = widget.description
        if (description != null) {
            children.add { Text(description, style = uiTextStyle(color = _textMuted, fontSize = 12.0, height = 1.5)) }
            children.add(_gap(10.dp))
        }
        children.add {
            Input(
                modifier = Modifier.testTag(personalMusicPlaylistNameInputTag),
                controller = _controller,
                focusNode = _focusNode,
                hintText = if (widget.initialName.isEmpty()) "歌单名称" else "",
                textInputAction = ImeAction.Done,
                onSubmitted = { _ -> _submit() },
            )
        }
        if (widget.loadImportPlaylists != null) {
            children.add(_gap(10.dp))
            children.add {
                Button(
                    modifier = Modifier.testTag(importPersonalMusicPlaylistButtonTag),
                    width = Dp.Infinity,
                    loading = _loadingImportPlaylists,
                    selected = _importPlaylist != null,
                    tone = if (_importPlaylist == null) ButtonTone.neutral else ButtonTone.primary,
                    icon = { Icon(Icons.Outlined.LibraryAdd) },
                    onPressed = { unawaited { _chooseImportPlaylist() } },
                ) {
                    Text("导入我的歌单")
                }
            }
            val importPlaylist = _importPlaylist
            if (importPlaylist != null) {
                children.add(_gap(8.dp))
                children.add {
                    // Flutter's Container adds the 1 dp border to its padding.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .testTag(selectedPersonalMusicPlaylistImportTag)
                            .decoration(_primaryDark, RoundedCornerShape(8.dp), UiColors.selectedBorder)
                            .padding(EdgeInsets.fromLTRB(13.dp, 9.dp, 7.dp, 9.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, color = _cyan, size = 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(importPlaylist.name, style = uiTextStyle(color = _textPrimary, fontSize = 13.0, fontWeight = FontWeight.W600))
                            Spacer(Modifier.height(2.dp))
                            Text("${importPlaylist.itemCount} 首歌曲", style = uiTextStyle(color = _textMuted, fontSize = 12.0))
                        }
                        ButtonIcon(
                            modifier = Modifier.testTag(clearPersonalMusicPlaylistImportTag),
                            icon = { Icon(Icons.Filled.Close) },
                            tooltip = "取消导入",
                            onPressed = { setState { _importPlaylist = null } },
                            size = 32.dp,
                        )
                    }
                }
            }
        }
        val error = _error
        if (error != null) {
            children.add(_gap(8.dp))
            children.add { _MusicPlaylistErrorText(error) }
        }
        DialogFrame(
            title = widget.title,
            icon = widget.icon,
            adaptiveActions =
                listOf(
                    ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop() }),
                    ResponsiveDialogAction(
                        label = widget.confirmLabel,
                        icon = widget.confirmIcon,
                        tone = ButtonTone.primary,
                        onPressed = ::_submit,
                    ),
                ),
            child = { _StretchColumn(children) },
        )
    }
}

@Composable
private fun _MusicPlaylistImportDialog(
    playlists: List<PersonalMusicPlaylist>,
    selectedPlaylistId: String?,
    title: String = "选择歌单",
    emptyText: String = "我的歌单为空",
    pickerKey: String = "personal-music-playlist-import-picker",
    optionKeyPrefix: String = "personal-music-playlist-import-option",
) {
    val context = rememberBuildContext()
    val viewportHeight = (MediaQuery.current.size.height * 0.56f).coerceIn(180.dp, 520.dp)
    DialogFrame(
        title = title,
        icon = Icons.Outlined.LibraryMusic,
        maxWidth = 560.dp,
        adaptiveActions = listOf(ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop() })),
        child = {
            Box(Modifier.fillMaxWidth().height(viewportHeight).testTag(pickerKey)) {
                if (playlists.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { _SettingsEmptyState(text = emptyText) }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(playlists.size, key = { index -> playlists[index].id }) { index ->
                            val playlist = playlists[index]
                            _MusicPlaylistImportTile(
                                playlist = playlist,
                                selected = playlist.id == selectedPlaylistId,
                                optionKeyPrefix = optionKeyPrefix,
                                onPressed = { Navigator.of(context).pop(playlist) },
                            )
                        }
                    }
                }
            }
        },
    )
}

private val _importTileNameStyle = uiTextStyle(color = _textPrimary, fontSize = 13.0, fontWeight = FontWeight.W600, height = 1.25)

@Composable
private fun _MusicPlaylistImportTile(
    playlist: PersonalMusicPlaylist,
    selected: Boolean,
    onPressed: () -> Unit,
    optionKeyPrefix: String = "personal-music-playlist-import-option",
) {
    val context = rememberBuildContext()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val namePainter =
            TextPainter(playlist.name, _importTileNameStyle).layout(context, maxWidth = (maxWidth - 118.dp).coerceAtLeast(40.dp))
        val measuredHeight = namePainter.height.dp + 39.dp
        val height = if (measuredHeight < 58.dp) 58.dp else measuredHeight
        key(playlist.id) {
            PressableSurface(
                modifier = Modifier.fillMaxWidth().testTag("$optionKeyPrefix-${playlist.id}"),
                height = height,
                selected = selected,
                onPressed = onPressed,
                padding = EdgeInsets.symmetric(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, color = _cyan, size = 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(playlist.name, modifier = Modifier.weight(1f), style = _importTileNameStyle)
                    Spacer(Modifier.width(10.dp))
                    Text("${playlist.itemCount} 首", style = uiTextStyle(color = _textMuted, fontSize = 12.0))
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (selected) Icons.Filled.Check else Icons.Filled.ChevronRight,
                        color = if (selected) _cyan else _textMuted,
                        size = 20.dp,
                    )
                }
            }
        }
    }
}
