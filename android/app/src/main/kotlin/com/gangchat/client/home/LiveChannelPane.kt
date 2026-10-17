package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MusicBoxController
import com.gangchat.client.app.liveParticipantByUserId
import com.gangchat.client.app.liveScreenShareViewers
import com.gangchat.client.app.visibleLiveParticipantsForStage
import com.gangchat.client.live.LiveVideoTrack
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.MusicBoxPlaybackMode
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.protocol.musicBoxBotIdentity
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.widgets.BoxShadow
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.OverlayPortal
import com.gangchat.client.widgets.OverlayPortalController
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import kotlin.math.max
import kotlin.math.roundToInt

internal val _paneEdgeInset: Dp = 14.dp
internal val _paneTopInset: Dp = _paneEdgeInset
internal val _liveRoomRadius: Dp = UiRadii.md
internal val _liveRoomPadding: Dp = 18.dp

// Width of the right-docked music box panel when expanded. Wide enough for a
// song row to carry its index, title, requester, and two actions.
internal val _musicBoxPanelWidth: Dp = 340.dp
internal const val _memberCardWidth: Double = 154.0
internal const val _memberCardMinTwoColumnWidth: Double = 135.0
internal const val _memberCardSpacing: Double = 12.0
internal const val _memberCardSurfaceHeight: Double = _memberCardWidth
internal const val _controlButtonSize: Double = 44.0
internal const val _controlHoverInfoBelowReserve: Double = 4.0
internal val _controlHoverInfoVerticalOffset: Dp = 24.dp
internal const val _liveStageControlGap: Double = 16.0

// The open music box always has its 44px inline player above one 44px
// transport row in the standard layout. Narrow layouts may add more rows, but
// those rows must move the docked panel upward instead of shortening it.
internal const val _musicBoxStandardControlHeight: Double = _controlButtonSize * 2 + 10.0
internal val _liveRoomBackground: Color = UiColors.surfaceLow
internal val _liveRoomBorder: Color = UiColors.border
internal val _memberSpeakingBackground = Color(0xFF252A34)
internal val _memberIdleBackground = Color(0xFF1D2328)

/** Test tag of the music box panel (Dart's `ValueKey('live-music-box-panel')`). */
const val liveMusicBoxPanelTag = "live-music-box-panel"

/** Test tag of every [com.gangchat.client.live.LiveVideoTrackView] the pane renders (Dart's `find.byType(LiveVideoTrackView)`). */
const val liveVideoTrackViewTag = "live-video-track-view"

enum class LiveStageSelectionMode { none, track }

class LiveStageSelection private constructor(
    val mode: LiveStageSelectionMode,
    val identity: String?,
    val isScreenShare: Boolean?,
) {
    companion object {
        fun track(identity: String, isScreenShare: Boolean): LiveStageSelection =
            LiveStageSelection(LiveStageSelectionMode.track, identity, isScreenShare)

        fun none(): LiveStageSelection = LiveStageSelection(LiveStageSelectionMode.none, null, null)

        fun fromTrack(track: LiveVideoTrack): LiveStageSelection =
            track(identity = track.identity, isScreenShare = track.isScreenShare)
    }
}

/**
 * The voice channel pane: room header, the member stage (with the focused
 * camera/screen-share stage above it), the live control bar, and the docked
 * music box console.
 *
 * Volumes are 0-1 except [participantVoiceVolume] (0-2). [inputVolume] is the
 * local microphone input preference (0 mutes the sent mic without writing
 * system input gain); [outputVolume] is the local voice listening volume for
 * ordinary remote speakers; [musicBoxVolume] the local listening volume for
 * the music box bot (restored from the store and persisted by
 * [onMusicBoxVolumeChanged]); [screenShareVolume] the local listening volume
 * for remote screen-share audio, separate from voice output.
 *
 * [suspendStageVideo] keeps the stage chrome mounted while releasing its
 * native video texture (Dart used it on Windows while the full-screen overlay
 * rendered the same track).
 */
@Composable
fun LiveChannelPane(
    title: String,
    avatarUrl: String?,
    live: LiveState?,
    currentUser: CurrentUser,
    loading: Boolean,
    joined: Boolean,
    joining: Boolean,
    leaving: Boolean,
    micMuted: Boolean,
    headphonesMuted: Boolean,
    voiceBlocked: Boolean,
    cameraOn: Boolean,
    screenSharing: Boolean,
    speakingUserIds: Set<String>,
    connectedParticipantIds: Set<String>,
    liveKitMicMutedByParticipantId: Map<String, Boolean>,
    videoTracks: List<LiveVideoTrack>,
    stageSelection: LiveStageSelection?,
    onStageSelectionChanged: (LiveStageSelection?) -> Unit,
    onEnterFullScreen: (LiveVideoTrack) -> Unit,
    onBackToChat: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onToggleMic: (() -> Unit)?,
    onToggleHeadphones: () -> Unit,
    onToggleCamera: () -> Unit,
    onFlipCamera: (suspend () -> Boolean)?,
    onSetLocalCameraMirrored: suspend (Boolean) -> Boolean,
    onToggleShare: (() -> Unit)?,
    musicBox: MusicBoxState?,
    musicBoxOpen: Boolean,
    musicBoxSearchController: TextEditingController,
    musicBoxSearchResults: List<MusicBoxSearchResult>,
    musicBoxSearching: Boolean,
    musicBoxSearchError: String?,
    musicBoxSource: String,
    onToggleMusicBox: () -> Unit,
    onMusicBoxTogglePlayback: () -> Unit,
    onMusicBoxSkip: () -> Unit,
    onMusicBoxQueueResult: (MusicBoxSearchResult) -> Unit,
    onMusicBoxRemoveItem: (MusicBoxQueueItem) -> Unit,
    onMusicBoxSourceChanged: (String) -> Unit,
    inputVolume: Double,
    outputVolume: Double,
    musicBoxVolume: Double,
    screenShareVolume: Double,
    participantVoiceVolume: (userId: String) -> Double,
    onInputVolumeChanged: (Double) -> Unit,
    onOutputVolumeChanged: (Double) -> Unit,
    onMusicBoxVolumeChanged: (Double) -> Unit,
    onScreenShareVolumeChanged: (Double) -> Unit,
    onScreenShareMuteToggled: () -> Unit,
    onParticipantVoiceVolumeChanged: (userId: String, volume: Double) -> Unit,
    onParticipantVoiceMuteToggled: (userId: String) -> Unit,
    canModerateParticipant: (LiveParticipant) -> Boolean,
    onToggleParticipantMicModeration: (LiveParticipant) -> Unit,
    onToggleParticipantHeadphonesModeration: (LiveParticipant) -> Unit,
    canRemoveParticipant: (LiveParticipant) -> Boolean,
    onRemoveParticipant: (LiveParticipant) -> Unit,
    modifier: Modifier = Modifier,
    suspendStageVideo: Boolean = false,
    onMusicBoxPrevious: (() -> Unit)? = null,
    onMusicBoxModeChanged: ((MusicBoxPlaybackMode) -> Unit)? = null,
    musicBoxController: MusicBoxController? = null,
    musicBoxRoomId: String? = null,
    musicPlaylistsRevision: Int = 0,
    musicBoxRoom: PublicRoom? = null,
    onMusicBoxStateChanged: ((MusicBoxState) -> Unit)? = null,
    onCreateFirstRoomMusicPlaylist: (() -> Unit)? = null,
    onCreateFirstPersonalMusicPlaylist: (() -> Unit)? = null,
    onEditRoomMusicPlaylist: MusicPlaylistEditCallback? = null,
    onEditPersonalMusicPlaylist: MusicPlaylistEditCallback? = null,
    onResolveParticipantProfile: UserProfileResolver? = null,
    onResolveParticipantRoomProfile: RoomProfileResolver? = null,
    onEnterParticipantProfileRoom: ((PublicRoom) -> Unit)? = null,
    participantProfileActionBuilder: UserProfileActionBuilder? = null,
) = StatefulWidget(
    LiveChannelPaneWidget(
        title = title,
        avatarUrl = avatarUrl,
        live = live,
        currentUser = currentUser,
        loading = loading,
        joined = joined,
        joining = joining,
        leaving = leaving,
        micMuted = micMuted,
        headphonesMuted = headphonesMuted,
        voiceBlocked = voiceBlocked,
        cameraOn = cameraOn,
        screenSharing = screenSharing,
        speakingUserIds = speakingUserIds,
        connectedParticipantIds = connectedParticipantIds,
        liveKitMicMutedByParticipantId = liveKitMicMutedByParticipantId,
        videoTracks = videoTracks,
        stageSelection = stageSelection,
        suspendStageVideo = suspendStageVideo,
        onStageSelectionChanged = onStageSelectionChanged,
        onEnterFullScreen = onEnterFullScreen,
        onBackToChat = onBackToChat,
        onJoin = onJoin,
        onLeave = onLeave,
        onToggleMic = onToggleMic,
        onToggleHeadphones = onToggleHeadphones,
        onToggleCamera = onToggleCamera,
        onFlipCamera = onFlipCamera,
        onSetLocalCameraMirrored = onSetLocalCameraMirrored,
        onToggleShare = onToggleShare,
        musicBox = musicBox,
        musicBoxOpen = musicBoxOpen,
        musicBoxSearchController = musicBoxSearchController,
        musicBoxSearchResults = musicBoxSearchResults,
        musicBoxSearching = musicBoxSearching,
        musicBoxSearchError = musicBoxSearchError,
        musicBoxSource = musicBoxSource,
        onToggleMusicBox = onToggleMusicBox,
        onMusicBoxTogglePlayback = onMusicBoxTogglePlayback,
        onMusicBoxSkip = onMusicBoxSkip,
        onMusicBoxPrevious = onMusicBoxPrevious,
        onMusicBoxModeChanged = onMusicBoxModeChanged,
        musicBoxController = musicBoxController,
        musicBoxRoomId = musicBoxRoomId,
        musicPlaylistsRevision = musicPlaylistsRevision,
        musicBoxRoom = musicBoxRoom,
        onMusicBoxStateChanged = onMusicBoxStateChanged,
        onCreateFirstRoomMusicPlaylist = onCreateFirstRoomMusicPlaylist,
        onCreateFirstPersonalMusicPlaylist = onCreateFirstPersonalMusicPlaylist,
        onEditRoomMusicPlaylist = onEditRoomMusicPlaylist,
        onEditPersonalMusicPlaylist = onEditPersonalMusicPlaylist,
        onMusicBoxQueueResult = onMusicBoxQueueResult,
        onMusicBoxRemoveItem = onMusicBoxRemoveItem,
        onMusicBoxSourceChanged = onMusicBoxSourceChanged,
        inputVolume = inputVolume,
        outputVolume = outputVolume,
        musicBoxVolume = musicBoxVolume,
        screenShareVolume = screenShareVolume,
        participantVoiceVolume = participantVoiceVolume,
        onInputVolumeChanged = onInputVolumeChanged,
        onOutputVolumeChanged = onOutputVolumeChanged,
        onMusicBoxVolumeChanged = onMusicBoxVolumeChanged,
        onScreenShareVolumeChanged = onScreenShareVolumeChanged,
        onScreenShareMuteToggled = onScreenShareMuteToggled,
        onParticipantVoiceVolumeChanged = onParticipantVoiceVolumeChanged,
        onParticipantVoiceMuteToggled = onParticipantVoiceMuteToggled,
        canModerateParticipant = canModerateParticipant,
        onToggleParticipantMicModeration = onToggleParticipantMicModeration,
        onToggleParticipantHeadphonesModeration = onToggleParticipantHeadphonesModeration,
        canRemoveParticipant = canRemoveParticipant,
        onRemoveParticipant = onRemoveParticipant,
        onResolveParticipantProfile = onResolveParticipantProfile,
        onResolveParticipantRoomProfile = onResolveParticipantRoomProfile,
        onEnterParticipantProfileRoom = onEnterParticipantProfileRoom,
        participantProfileActionBuilder = participantProfileActionBuilder,
        modifier = modifier,
    ),
) { _LiveChannelPaneState() }

class LiveChannelPaneWidget(
    val title: String,
    val avatarUrl: String?,
    val live: LiveState?,
    val currentUser: CurrentUser,
    val loading: Boolean,
    val joined: Boolean,
    val joining: Boolean,
    val leaving: Boolean,
    val micMuted: Boolean,
    val headphonesMuted: Boolean,
    val voiceBlocked: Boolean,
    val cameraOn: Boolean,
    val screenSharing: Boolean,
    val speakingUserIds: Set<String>,
    val connectedParticipantIds: Set<String>,
    val liveKitMicMutedByParticipantId: Map<String, Boolean>,
    val videoTracks: List<LiveVideoTrack>,
    val stageSelection: LiveStageSelection?,
    val suspendStageVideo: Boolean,
    val onStageSelectionChanged: (LiveStageSelection?) -> Unit,
    val onEnterFullScreen: (LiveVideoTrack) -> Unit,
    val onBackToChat: () -> Unit,
    val onJoin: () -> Unit,
    val onLeave: () -> Unit,
    val onToggleMic: (() -> Unit)?,
    val onToggleHeadphones: () -> Unit,
    val onToggleCamera: () -> Unit,
    val onFlipCamera: (suspend () -> Boolean)?,
    val onSetLocalCameraMirrored: suspend (Boolean) -> Boolean,
    val onToggleShare: (() -> Unit)?,
    val musicBox: MusicBoxState?,
    val musicBoxOpen: Boolean,
    val musicBoxSearchController: TextEditingController,
    val musicBoxSearchResults: List<MusicBoxSearchResult>,
    val musicBoxSearching: Boolean,
    val musicBoxSearchError: String?,
    val musicBoxSource: String,
    val onToggleMusicBox: () -> Unit,
    val onMusicBoxTogglePlayback: () -> Unit,
    val onMusicBoxSkip: () -> Unit,
    val onMusicBoxPrevious: (() -> Unit)?,
    val onMusicBoxModeChanged: ((MusicBoxPlaybackMode) -> Unit)?,
    val musicBoxController: MusicBoxController?,
    val musicBoxRoomId: String?,
    val musicPlaylistsRevision: Int,
    val musicBoxRoom: PublicRoom?,
    val onMusicBoxStateChanged: ((MusicBoxState) -> Unit)?,
    val onCreateFirstRoomMusicPlaylist: (() -> Unit)?,
    val onCreateFirstPersonalMusicPlaylist: (() -> Unit)?,
    val onEditRoomMusicPlaylist: MusicPlaylistEditCallback?,
    val onEditPersonalMusicPlaylist: MusicPlaylistEditCallback?,
    val onMusicBoxQueueResult: (MusicBoxSearchResult) -> Unit,
    val onMusicBoxRemoveItem: (MusicBoxQueueItem) -> Unit,
    val onMusicBoxSourceChanged: (String) -> Unit,
    val inputVolume: Double,
    val outputVolume: Double,
    val musicBoxVolume: Double,
    val screenShareVolume: Double,
    val participantVoiceVolume: (String) -> Double,
    val onInputVolumeChanged: (Double) -> Unit,
    val onOutputVolumeChanged: (Double) -> Unit,
    val onMusicBoxVolumeChanged: (Double) -> Unit,
    val onScreenShareVolumeChanged: (Double) -> Unit,
    val onScreenShareMuteToggled: () -> Unit,
    val onParticipantVoiceVolumeChanged: (String, Double) -> Unit,
    val onParticipantVoiceMuteToggled: (String) -> Unit,
    val canModerateParticipant: (LiveParticipant) -> Boolean,
    val onToggleParticipantMicModeration: (LiveParticipant) -> Unit,
    val onToggleParticipantHeadphonesModeration: (LiveParticipant) -> Unit,
    val canRemoveParticipant: (LiveParticipant) -> Boolean,
    val onRemoveParticipant: (LiveParticipant) -> Unit,
    val onResolveParticipantProfile: UserProfileResolver?,
    val onResolveParticipantRoomProfile: RoomProfileResolver?,
    val onEnterParticipantProfileRoom: ((PublicRoom) -> Unit)?,
    val participantProfileActionBuilder: UserProfileActionBuilder?,
    val modifier: Modifier,
)

private class _LiveChannelPaneState : ViewState<LiveChannelPaneWidget>() {
    // A ViewState without mutable fields is inferred stable by the Compose
    // compiler, so build() and the slot lambdas capturing this state would
    // skip when the parent passes a new widget. Flutter always rebuilds.
    @Suppress("unused")
    private var _rebuildWithParent = true

    private fun _resolveStageTrack(): LiveVideoTrack? =
        _resolveLiveStageTrack(tracks = widget.videoTracks, selection = widget.stageSelection)

    fun _selectStage(track: LiveVideoTrack) {
        _selectStageAndJoinIfNeeded(LiveStageSelection.fromTrack(track))
    }

    fun _selectScreenShareStage(identity: String) {
        _selectStageAndJoinIfNeeded(LiveStageSelection.track(identity = identity, isScreenShare = true))
    }

    fun _selectCameraStage(identity: String) {
        _selectStageAndJoinIfNeeded(LiveStageSelection.track(identity = identity, isScreenShare = false))
    }

    private fun _selectStageAndJoinIfNeeded(selection: LiveStageSelection) {
        widget.onStageSelectionChanged(selection)
        if (!widget.joined && !widget.joining && !widget.leaving && !widget.loading) {
            widget.onJoin()
        }
    }

    fun _exitStage() {
        widget.onStageSelectionChanged(LiveStageSelection.none())
    }

    @Composable
    override fun build() {
        val participants =
            visibleLiveParticipantsForStage(
                widget.live?.participants ?: emptyList(),
                currentUserId = widget.currentUser.id,
                localParticipantReady = widget.joined && !widget.joining,
                connectedParticipantIds = widget.connectedParticipantIds,
            ).filter { p -> p.user.id != musicBoxBotIdentity }
        val stageTrack = _resolveStageTrack()
        val musicBox = widget.musicBox
        val musicBoxEnabled = musicBox?.enabled ?: false
        val musicBoxOpen = widget.joined && musicBoxEnabled && widget.musicBoxOpen
        val roomShape = RoundedCornerShape(_liveRoomRadius)
        Box(
            widget.modifier
                .decoration(UiColors.background)
                .padding(start = _paneEdgeInset, top = _paneTopInset, end = _paneEdgeInset, bottom = _paneEdgeInset),
            propagateMinConstraints = true,
        ) {
            Column(
                Modifier
                    .boxShadow(listOf(BoxShadow(color = Color(0x66080A0D), offset = DpOffset(0.dp, 10.dp), blurRadius = 22.dp)), roomShape)
                    .decoration(_liveRoomBackground, roomShape, _liveRoomBorder)
                    .padding(_liveRoomPadding),
            ) {
                _LiveRoomHeader(title = widget.title, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                _LiveChannelBodyLayout(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    stage = { _Stage(participants, stageTrack) },
                    controls = {
                        _LiveControlBar(
                            joined = widget.joined,
                            joining = widget.joining,
                            micMuted = widget.micMuted,
                            headphonesMuted = widget.headphonesMuted,
                            voiceBlocked = widget.voiceBlocked,
                            cameraOn = widget.cameraOn,
                            screenSharing = widget.screenSharing,
                            inputVolume = widget.inputVolume,
                            outputVolume = widget.outputVolume,
                            musicBox = musicBox,
                            musicBoxEnabled = musicBoxEnabled,
                            musicBoxOpen = musicBoxOpen,
                            onJoin = if (widget.loading || widget.leaving) null else widget.onJoin,
                            onLeave = widget.onLeave,
                            onToggleMic = widget.onToggleMic,
                            onToggleHeadphones = widget.onToggleHeadphones,
                            onToggleCamera = widget.onToggleCamera,
                            onToggleShare = widget.onToggleShare,
                            onInputVolumeChanged = widget.onInputVolumeChanged,
                            onOutputVolumeChanged = widget.onOutputVolumeChanged,
                            onToggleMusicBox = widget.onToggleMusicBox,
                            onMusicBoxTogglePlayback = widget.onMusicBoxTogglePlayback,
                            onMusicBoxSkip = widget.onMusicBoxSkip,
                            musicBoxController = widget.musicBoxController,
                            musicBoxRoomId = widget.musicBoxRoomId,
                            onMusicBoxStateChanged = widget.onMusicBoxStateChanged,
                            currentUser = widget.currentUser,
                            onResolveUserProfile = widget.onResolveParticipantProfile,
                            onResolveRoomProfile = widget.onResolveParticipantRoomProfile,
                            onEnterCommonRoom = widget.onEnterParticipantProfileRoom,
                            userProfileActionBuilder = widget.participantProfileActionBuilder,
                            onCollapse = widget.onBackToChat,
                        )
                    },
                    musicBox =
                        if (musicBoxOpen && musicBox != null) {
                            { panelModifier -> _MusicBoxPanel(musicBox, panelModifier) }
                        } else {
                            null
                        },
                )
            }
        }
    }

    @Composable
    private fun _MusicBoxPanel(musicBox: MusicBoxState, modifier: Modifier) {
        LiveMusicBoxPanel(
            modifier = modifier.testTag(liveMusicBoxPanelTag),
            state = musicBox,
            searchController = widget.musicBoxSearchController,
            searchResults = widget.musicBoxSearchResults,
            searching = widget.musicBoxSearching,
            searchError = widget.musicBoxSearchError,
            source = widget.musicBoxSource,
            onTogglePlayback = widget.onMusicBoxTogglePlayback,
            onSkip = widget.onMusicBoxSkip,
            onPrevious = widget.onMusicBoxPrevious,
            onModeChanged = widget.onMusicBoxModeChanged,
            controller = widget.musicBoxController,
            roomId = widget.musicBoxRoomId,
            playlistsRevision = widget.musicPlaylistsRevision,
            room = widget.musicBoxRoom,
            onStateChanged = widget.onMusicBoxStateChanged,
            currentUser = widget.currentUser,
            onResolveUserProfile = widget.onResolveParticipantProfile,
            onResolveRoomProfile = widget.onResolveParticipantRoomProfile,
            onEnterCommonRoom = widget.onEnterParticipantProfileRoom,
            userProfileActionBuilder = widget.participantProfileActionBuilder,
            onCreateFirstRoomPlaylist = widget.onCreateFirstRoomMusicPlaylist,
            onCreateFirstPersonalPlaylist = widget.onCreateFirstPersonalMusicPlaylist,
            onEditRoomPlaylist = widget.onEditRoomMusicPlaylist,
            onEditPersonalPlaylist = widget.onEditPersonalMusicPlaylist,
            onQueueResult = widget.onMusicBoxQueueResult,
            onRemoveItem = widget.onMusicBoxRemoveItem,
            onSourceChanged = widget.onMusicBoxSourceChanged,
            onClose = widget.onToggleMusicBox,
            volume = widget.musicBoxVolume,
            onVolumeChanged = widget.onMusicBoxVolumeChanged,
        )
    }

    @Composable
    private fun _Stage(participants: List<LiveParticipant>, stageTrack: LiveVideoTrack?) {
        Column(Modifier.fillMaxSize()) {
            if (stageTrack != null) {
                Box(Modifier.weight(3f).fillMaxWidth(), propagateMinConstraints = true) {
                    _LiveMediaStage(
                        track = stageTrack,
                        renderVideo = !widget.suspendStageVideo,
                        label = liveStageTrackLabel(widget.live, stageTrack),
                        screenShareViewers =
                            if (stageTrack.isScreenShare) liveScreenShareViewers(widget.live, stageTrack.identity) else emptyList(),
                        screenShareVolume = widget.screenShareVolume,
                        cameraMirrored = liveParticipantByUserId(widget.live, stageTrack.identity)?.cameraMirrored ?: false,
                        onFlipCamera = if (stageTrack.isLocal) widget.onFlipCamera else null,
                        onSetLocalCameraMirrored = widget.onSetLocalCameraMirrored,
                        onExit = ::_exitStage,
                        onFullScreen = { widget.onEnterFullScreen(stageTrack) },
                        onScreenShareVolumeChanged = widget.onScreenShareVolumeChanged,
                        onScreenShareMuteToggled = widget.onScreenShareMuteToggled,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            Box(Modifier.weight(if (stageTrack == null) 1f else 2f).fillMaxWidth(), propagateMinConstraints = true) {
                _LiveMemberStage(
                    loading = widget.loading,
                    participants = participants,
                    currentUser = widget.currentUser,
                    localMicMuted = widget.micMuted,
                    localHeadphonesMuted = widget.headphonesMuted,
                    speakingUserIds = widget.speakingUserIds,
                    liveKitMicMutedByParticipantId = widget.liveKitMicMutedByParticipantId,
                    videoTracks = widget.videoTracks,
                    stageTrack = stageTrack,
                    onSelectStage = ::_selectStage,
                    onSelectScreenShareStage = ::_selectScreenShareStage,
                    onSelectCameraStage = ::_selectCameraStage,
                    onToggleMic = widget.onToggleMic,
                    onToggleHeadphones = widget.onToggleHeadphones,
                    participantVoiceVolume = widget.participantVoiceVolume,
                    onParticipantVoiceVolumeChanged = widget.onParticipantVoiceVolumeChanged,
                    onParticipantVoiceMuteToggled = widget.onParticipantVoiceMuteToggled,
                    canModerateParticipant = widget.canModerateParticipant,
                    onToggleParticipantMicModeration = widget.onToggleParticipantMicModeration,
                    onToggleParticipantHeadphonesModeration = widget.onToggleParticipantHeadphonesModeration,
                    canRemoveParticipant = widget.canRemoveParticipant,
                    onRemoveParticipant = widget.onRemoveParticipant,
                    onResolveParticipantProfile = widget.onResolveParticipantProfile,
                    onResolveParticipantRoomProfile = widget.onResolveParticipantRoomProfile,
                    onEnterParticipantProfileRoom = widget.onEnterParticipantProfileRoom,
                    participantProfileActionBuilder = widget.participantProfileActionBuilder,
                )
            }
        }
    }
}

/**
 * Lays out the live stage and its controls in one pass so the docked music box
 * can keep a stable height. Extra compact control rows reduce the stage and
 * move the panel upward; they never compress the panel itself.
 */
@Composable
private fun _LiveChannelBodyLayout(
    stage: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    musicBox: (@Composable (Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) = StatefulWidget(_LiveChannelBodyLayoutWidget(stage, controls, musicBox, modifier)) { _LiveChannelBodyLayoutState() }

private class _LiveChannelBodyLayoutWidget(
    val stage: @Composable () -> Unit,
    val controls: @Composable () -> Unit,
    val musicBox: (@Composable (Modifier) -> Unit)?,
    val modifier: Modifier,
)

private class _LiveChannelBodyLayoutState : ViewState<_LiveChannelBodyLayoutWidget>() {
    // A ViewState without mutable fields is inferred stable by the Compose
    // compiler, so build() and the slot lambdas capturing this state would
    // skip when the parent passes a new widget. Flutter always rebuilds.
    @Suppress("unused")
    private var _rebuildWithParent = true

    private val _bodyKey = _LiveBoundsHandle()
    private val _musicBoxAnchorKey = _LiveBoundsHandle()
    private val _musicBoxPortal = OverlayPortalController()

    override fun dispose() {
        if (_musicBoxPortal.isShowing) _musicBoxPortal.hide()
    }

    // Dart syncs the portal in a post-frame callback; a side effect runs once
    // the composition has been applied, before the next frame lays it out.
    private fun _syncPortal() {
        if (!mounted) return
        if (widget.musicBox != null) {
            if (!_musicBoxPortal.isShowing) _musicBoxPortal.show()
        } else if (_musicBoxPortal.isShowing) {
            _musicBoxPortal.hide()
        }
    }

    @Composable
    private fun _OverlayChild() {
        // The overlay fills the window, so overlay and window coordinates agree.
        val bodyRect = _bodyKey.rect
        val anchorRect = _musicBoxAnchorKey.rect
        val musicBox = widget.musicBox
        if (bodyRect == null || anchorRect == null || musicBox == null) return
        val standardPanelHeight =
            (bodyRect.bottom - bodyRect.top).value - _musicBoxStandardControlHeight - _liveStageControlGap - _controlHoverInfoBelowReserve
        val desiredPanelHeight =
            if (standardPanelHeight < _musicBoxMinComfortableHeight) _musicBoxMinComfortableHeight else standardPanelHeight
        val panelHeight = if (desiredPanelHeight > anchorRect.top.value) anchorRect.top.value.toDouble() else desiredPanelHeight
        val bodyWidth = bodyRect.right - bodyRect.left
        val panelWidth = if (bodyWidth < _musicBoxPanelWidth) bodyWidth else _musicBoxPanelWidth
        if (panelHeight <= 0 || panelWidth <= 0.dp) return
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .absoluteOffset(x = anchorRect.right - panelWidth, y = anchorRect.top - panelHeight.dp)
                    .size(panelWidth, panelHeight.dp),
                propagateMinConstraints = true,
            ) {
                musicBox(Modifier)
            }
        }
    }

    @Composable
    override fun build() {
        // Own semantics boundary so this portal's traversal never merges with
        // hover-card portals inside the body (see HoverCardAnchor).
        OverlayPortal(controller = _musicBoxPortal, overlayChildBuilder = { _ -> _OverlayChild() }) {
            Layout(
                modifier = widget.modifier.semantics { isTraversalGroup = true }.liveBounds(_bodyKey),
                contents =
                    listOf(
                        widget.stage,
                        widget.controls,
                        { Box(Modifier.liveBounds(_musicBoxAnchorKey)) },
                    ),
            ) { (stageMeasurables, controlMeasurables, anchorMeasurables), constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val controlConstraints = Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = Constraints.Infinity)
                val controlPlaceables = controlMeasurables.map { it.measure(controlConstraints) }
                val controlHeight = controlPlaceables.maxOfOrNull { it.height } ?: 0
                val gapPx = _liveStageControlGap.dp.toPx()
                val reservePx = _controlHoverInfoBelowReserve.dp.toPx()
                val stageHeight = (height - controlHeight - gapPx - reservePx).roundToInt().coerceIn(0, max(0, height))
                val stageConstraints = Constraints.fixed(width, stageHeight)
                val stagePlaceables = stageMeasurables.map { it.measure(stageConstraints) }
                val anchorPlaceables = anchorMeasurables.map { it.measure(Constraints.fixed(0, 0)) }
                layout(width, height) {
                    for (placeable in stagePlaceables) placeable.place(0, 0)
                    for (placeable in controlPlaceables) placeable.place(0, stageHeight + gapPx.roundToInt())
                    for (placeable in anchorPlaceables) placeable.place(width, stageHeight)
                }
            }
        }
        SideEffect { _syncPortal() }
        DisposableEffect(Unit) { onDispose { if (_musicBoxPortal.isShowing) _musicBoxPortal.hide() } }
    }
}

@Composable
internal fun _LiveRoomHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.GraphicEq, color = UiColors.textSecondary, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = UiTypography.title.copyWith(fontSize = 16.0),
        )
    }
}

/**
 * A box's rectangle in window coordinates (dp) with its ancestors' transforms
 * applied (Dart's `localToGlobal` of both corners), kept as snapshot state so
 * overlay layouts that read it follow the box.
 */
internal class _LiveBoundsHandle {
    var rect: DpRect? by mutableStateOf(null)
        internal set

    /** The untransformed layout size (Dart's `RenderBox.size`). */
    var size: DpSize = DpSize.Zero
        internal set

    /** The size of the window root (the overlay). */
    var rootSize: DpSize = DpSize.Zero
        internal set

    internal var coordinates: LayoutCoordinates? = null

    val hasSize: Boolean get() = coordinates?.isAttached == true && rect != null
}

internal fun Modifier.liveBounds(handle: _LiveBoundsHandle): Modifier =
    composed {
        val density = LocalDensity.current
        this.onGloballyPositioned { coords ->
            handle.coordinates = coords
            val root = coords.findRootCoordinates()
            // Dart's `localToGlobal` of both corners (also defined for the
            // zero-size music box anchor, unlike a bounding box).
            val topLeft = root.localPositionOf(coords, Offset.Zero)
            val bottomRight = root.localPositionOf(coords, Offset(coords.size.width.toFloat(), coords.size.height.toFloat()))
            with(density) {
                handle.size = DpSize(coords.size.width.toDp(), coords.size.height.toDp())
                handle.rootSize = DpSize(root.size.width.toDp(), root.size.height.toDp())
                val rect =
                    DpRect(
                        minOf(topLeft.x, bottomRight.x).toDp(),
                        minOf(topLeft.y, bottomRight.y).toDp(),
                        maxOf(topLeft.x, bottomRight.x).toDp(),
                        maxOf(topLeft.y, bottomRight.y).toDp(),
                    )
                if (handle.rect != rect) handle.rect = rect
            }
        }
    }

/** Flutter's `GestureDetector(onTap:)`: a tap that loses to drags and to an ancestor's long press. */
internal fun Modifier._liveTap(onTap: (() -> Unit)?): Modifier =
    if (onTap == null) {
        this
    } else {
        composed {
            val current = androidx.compose.runtime.rememberUpdatedState(onTap)
            this.pointerInput(Unit) { detectTapGestures(onTap = { current.value.invoke() }) }
        }
    }

/**
 * Flutter's `MouseRegion(onEnter:, onExit:)` for a mouse pointer. Like the
 * mouse tracker, it also reports an exit when a mouse move lands outside the
 * region.
 */
internal fun Modifier._liveMouseHover(onEnter: () -> Unit, onExit: () -> Unit): Modifier =
    composed {
        val enter = androidx.compose.runtime.rememberUpdatedState(onEnter)
        val exit = androidx.compose.runtime.rememberUpdatedState(onExit)
        this.pointerInput(Unit) {
            awaitPointerEventScope {
                var inside = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.type == PointerType.Mouse } ?: continue
                    val nowInside =
                        when (event.type) {
                            PointerEventType.Enter -> true
                            PointerEventType.Exit -> false
                            else ->
                                change.position.x >= 0f && change.position.y >= 0f &&
                                    change.position.x < size.width.toFloat() && change.position.y < size.height.toFloat()
                        }
                    if (nowInside == inside) continue
                    inside = nowInside
                    if (inside) enter.value.invoke() else exit.value.invoke()
                }
            }
        }
    }

/** A 1 dp top border painted behind the content (Dart's `Border(top: BorderSide(color))`). */
internal fun Modifier._liveTopBorder(color: Color): Modifier =
    this.drawBehind { drawRect(color, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }

/** The target opacity of an animated reveal/hover layer (Dart tests read `AnimatedOpacity.opacity`). */
val LiveOpacityKey = SemanticsPropertyKey<Float>("LiveOpacity")
var SemanticsPropertyReceiver.liveOpacity by LiveOpacityKey

/** The tint of a member status icon (Dart tests read `Icon.color`). */
val LiveIconTintKey = SemanticsPropertyKey<Color>("LiveIconTint")
var SemanticsPropertyReceiver.liveIconTint by LiveIconTintKey

/** Whether a music-box group header is unfolded (Dart's `Semantics(expanded:)`). */
val LiveExpandedKey = SemanticsPropertyKey<Boolean>("LiveExpanded")
var SemanticsPropertyReceiver.liveExpanded by LiveExpandedKey
