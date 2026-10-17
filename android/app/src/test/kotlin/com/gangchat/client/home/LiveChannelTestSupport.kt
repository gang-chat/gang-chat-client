package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.gangchat.client.app.MusicBoxController
import com.gangchat.client.config.AppConfig
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.live.FakeVideoTrack
import com.gangchat.client.live.LiveVideoTrack
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.MusicBoxActiveSource
import com.gangchat.client.protocol.MusicBoxActiveSourceType
import com.gangchat.client.protocol.MusicBoxCapabilities
import com.gangchat.client.protocol.MusicBoxPlayback
import com.gangchat.client.protocol.MusicBoxPlaybackMode
import com.gangchat.client.protocol.MusicBoxPlaybackState
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxQueueItemStatus
import com.gangchat.client.protocol.MusicBoxRequester
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.MusicBoxUsage
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PersonalMusicPlaylistApi
import com.gangchat.client.protocol.PersonalMusicPlaylistItem
import com.gangchat.client.protocol.PersonalMusicPlaylistItemsPage
import com.gangchat.client.protocol.PersonalMusicPlaylistPage
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomMusicPlaylistApi
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.dpBounds
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.IconSemanticsKey
import com.gangchat.client.widgets.TextEditingController

// Fixtures shared by the live channel pane and music box tests (Dart's
// `live_channel_pane_test.dart` / `music_box_progress_widget_test.dart`
// helpers).

val liveTestCurrentUser =
    CurrentUser(
        id = "current_user",
        uid = "1000000",
        username = "me",
        displayName = "Me",
        bio = "",
        gender = "secret",
        email = null,
        emailPublic = false,
        phoneNumber = null,
        phoneNumberPublic = false,
        avatarUrl = null,
        defaultAvatarKey = "blue-3",
        isSuperuser = false,
        createdAt = null,
    )

val liveTestAppConfig =
    AppConfig(
        apiBaseUrl = "https://api.test/api/v1",
        assetBaseUrl = "https://assets.test",
        releaseBucketUrl = "https://releases.test/gang-chat",
    )

fun liveState(participants: List<LiveParticipant>): LiveState =
    LiveState(
        roomId = "room_1",
        participantCount = participants.size,
        participants = participants,
        updatedAt = DateTime.utc(2026, 6, 11, 9),
    )

fun liveParticipant(
    id: String,
    user: UserSummary,
    micMuted: Boolean = false,
    micBlocked: Boolean = false,
    headphonesMuted: Boolean = false,
    headphonesBlocked: Boolean = false,
    voiceBlocked: Boolean = false,
    cameraOn: Boolean = false,
    cameraMirrored: Boolean = false,
    screenSharing: Boolean = false,
    screenViewers: List<UserSummary> = emptyList(),
    connectionState: String = "connected",
): LiveParticipant =
    LiveParticipant(
        liveSessionId = id,
        user = user,
        joinedAt = DateTime.utc(2026, 6, 11, 9),
        micMuted = micMuted,
        micBlocked = micBlocked,
        headphonesMuted = headphonesMuted,
        headphonesBlocked = headphonesBlocked,
        headphonesListening = !headphonesMuted && !headphonesBlocked,
        voiceBlocked = voiceBlocked,
        cameraOn = cameraOn,
        cameraMirrored = cameraMirrored,
        screenSharing = screenSharing,
        screenViewers = screenViewers,
        connectionState = connectionState,
    )

fun liveUser(id: String, name: String, roomRole: String, isSuperuser: Boolean = false): UserSummary =
    UserSummary(
        id = id,
        username = id,
        displayName = name,
        avatarUrl = null,
        defaultAvatarKey = "blue-3",
        roomDisplayName = name,
        roomRole = roomRole,
        isSuperuser = isSuperuser,
    )

fun liveVideoTrack(identity: String, isScreenShare: Boolean, isLocal: Boolean): LiveVideoTrack =
    LiveVideoTrack(identity = identity, track = FakeVideoTrack(), isScreenShare = isScreenShare, isLocal = isLocal)

val emptyMusicBoxState =
    MusicBoxState(
        enabled = true,
        playback = MusicBoxPlayback(state = MusicBoxPlaybackState.stopped, currentItemId = "", positionMs = 0, volume = 100, updatedAt = null),
        queue = emptyList(),
        usage = MusicBoxUsage(usedBytes = 0, limitBytes = 200L * 1024 * 1024),
    )

/** Dart's `_host(...)` arguments for the pane; every field can change between pumps. */
class LivePaneParams(
    var live: LiveState = liveState(emptyList()),
    var speakingUserIds: Set<String> = emptySet(),
    var connectedParticipantIds: Set<String> = emptySet(),
    var liveKitMicMutedByParticipantId: Map<String, Boolean> = emptyMap(),
    var width: Double = 720.0,
    var height: Double = 520.0,
    var loading: Boolean = false,
    var joined: Boolean = true,
    var joining: Boolean = false,
    var leaving: Boolean = false,
    var micMuted: Boolean = false,
    var headphonesMuted: Boolean = false,
    var cameraOn: Boolean = false,
    var onJoin: () -> Unit = {},
    var onToggleMic: () -> Unit = {},
    var onToggleHeadphones: () -> Unit = {},
    var onToggleCamera: () -> Unit = {},
    var onFlipCamera: (suspend () -> Boolean)? = null,
    var onSetLocalCameraMirrored: suspend (Boolean) -> Boolean = { true },
    var onToggleShare: () -> Unit = {},
    var screenShareSupported: Boolean = true,
    var onStageSelectionChanged: (LiveStageSelection?) -> Unit = {},
    var onEnterFullScreen: (LiveVideoTrack) -> Unit = {},
    var onBackToChat: () -> Unit = {},
    var stageSelection: LiveStageSelection? = null,
    var videoTracks: List<LiveVideoTrack> = emptyList(),
    var suspendStageVideo: Boolean = false,
    var musicBox: MusicBoxState? = null,
    var musicBoxOpen: Boolean = false,
    var onToggleMusicBox: () -> Unit = {},
    var musicBoxSearchResults: List<MusicBoxSearchResult> = emptyList(),
    var musicBoxController: MusicBoxController? = null,
    var musicBoxRoomId: String? = null,
    var preserveKeyboardViewport: Boolean = false,
    var screenShareVolume: Double = 1.0,
    var onScreenShareVolumeChanged: (Double) -> Unit = {},
    var onScreenShareMuteToggled: () -> Unit = {},
    var inputVolume: Double = 1.0,
    var outputVolume: Double = 1.0,
    var participantVoiceVolume: (String) -> Double = { 1.0 },
    var onParticipantVoiceVolumeChanged: (String, Double) -> Unit = { _, _ -> },
    var onParticipantVoiceMuteToggled: (String) -> Unit = {},
    var canModerateParticipant: (LiveParticipant) -> Boolean = { false },
    var onToggleParticipantMicModeration: (LiveParticipant) -> Unit = {},
    var onToggleParticipantHeadphonesModeration: (LiveParticipant) -> Unit = {},
    var canRemoveParticipant: (LiveParticipant) -> Boolean = { false },
    var onRemoveParticipant: (LiveParticipant) -> Unit = {},
    var onResolveParticipantProfile: UserProfileResolver? = null,
    var compactLayout: Boolean? = null,
    var title: String = "Test room",
)

/** Dart's `_host`: the pane in a `SizedBox(width, height)` under an [AppConfigScope]. */
@Composable
fun LiveChannelPaneHost(params: LivePaneParams, searchController: TextEditingController) {
    val p = params
    val pane: @Composable () -> Unit = {
        AppConfigScope(liveTestAppConfig) {
            Box(Modifier.size(p.width.dp, p.height.dp)) {
                LiveChannelPane(
                    title = p.title,
                    avatarUrl = null,
                    live = p.live,
                    currentUser = liveTestCurrentUser,
                    loading = p.loading,
                    joined = p.joined,
                    joining = p.joining,
                    leaving = p.leaving,
                    micMuted = p.micMuted,
                    headphonesMuted = p.headphonesMuted,
                    voiceBlocked = false,
                    cameraOn = p.cameraOn,
                    screenSharing = false,
                    speakingUserIds = p.speakingUserIds,
                    connectedParticipantIds = p.connectedParticipantIds,
                    liveKitMicMutedByParticipantId = p.liveKitMicMutedByParticipantId,
                    videoTracks = p.videoTracks,
                    stageSelection = p.stageSelection ?: LiveStageSelection.none(),
                    suspendStageVideo = p.suspendStageVideo,
                    onStageSelectionChanged = p.onStageSelectionChanged,
                    onEnterFullScreen = p.onEnterFullScreen,
                    onBackToChat = p.onBackToChat,
                    onJoin = p.onJoin,
                    onLeave = {},
                    onToggleMic = p.onToggleMic,
                    onToggleHeadphones = p.onToggleHeadphones,
                    onToggleCamera = p.onToggleCamera,
                    onFlipCamera = p.onFlipCamera,
                    onSetLocalCameraMirrored = p.onSetLocalCameraMirrored,
                    onToggleShare = if (p.screenShareSupported) p.onToggleShare else null,
                    musicBox = p.musicBox,
                    musicBoxOpen = p.musicBoxOpen,
                    musicBoxSearchController = searchController,
                    musicBoxSearchResults = p.musicBoxSearchResults,
                    musicBoxSearching = false,
                    musicBoxSearchError = null,
                    musicBoxSource = "netease",
                    onToggleMusicBox = p.onToggleMusicBox,
                    onMusicBoxTogglePlayback = {},
                    onMusicBoxSkip = {},
                    onMusicBoxQueueResult = {},
                    onMusicBoxRemoveItem = {},
                    onMusicBoxSourceChanged = {},
                    musicBoxController = p.musicBoxController,
                    musicBoxRoomId = p.musicBoxRoomId,
                    inputVolume = p.inputVolume,
                    outputVolume = p.outputVolume,
                    musicBoxVolume = 1.0,
                    screenShareVolume = p.screenShareVolume,
                    onInputVolumeChanged = {},
                    onOutputVolumeChanged = {},
                    onMusicBoxVolumeChanged = {},
                    onScreenShareVolumeChanged = p.onScreenShareVolumeChanged,
                    onScreenShareMuteToggled = p.onScreenShareMuteToggled,
                    participantVoiceVolume = p.participantVoiceVolume,
                    onParticipantVoiceVolumeChanged = p.onParticipantVoiceVolumeChanged,
                    onParticipantVoiceMuteToggled = p.onParticipantVoiceMuteToggled,
                    canModerateParticipant = p.canModerateParticipant,
                    onToggleParticipantMicModeration = p.onToggleParticipantMicModeration,
                    onToggleParticipantHeadphonesModeration = p.onToggleParticipantHeadphonesModeration,
                    canRemoveParticipant = p.canRemoveParticipant,
                    onRemoveParticipant = p.onRemoveParticipant,
                    onResolveParticipantProfile = p.onResolveParticipantProfile,
                )
            }
        }
    }
    val withKeyboard: @Composable () -> Unit = {
        if (p.preserveKeyboardViewport) HomeKeyboardOverlayViewport(enabled = true, modifier = Modifier.fillMaxSize()) { Box { pane() } } else pane()
    }
    val compact = p.compactLayout
    if (compact == null) withKeyboard() else HomeAdaptiveLayout(compact) { withKeyboard() }
}

/**
 * Holds [LivePaneParams] in snapshot state so a test can "pump" new
 * arguments (Dart's repeated `pumpWidget(_host(...))`).
 */
class LivePaneHarness(initial: LivePaneParams) {
    var params by mutableStateOf(initial)
    val searchController = TextEditingController()

    fun pump(update: LivePaneParams.() -> Unit) {
        val next = params.copyParams()
        next.update()
        params = next
    }
}

private fun LivePaneParams.copyParams(): LivePaneParams {
    val source = this
    return LivePaneParams().also { copy ->
        for (field in LivePaneParams::class.java.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
            field.isAccessible = true
            field.set(copy, field.get(source))
        }
    }
}

fun ComposeContentTestRule.setLivePane(
    harness: LivePaneHarness,
    viewPadding: EdgeInsets? = null,
) {
    setHomeContent(viewPadding = viewPadding) { _ -> LiveChannelPaneHost(harness.params, harness.searchController) }
}

// --- Music box fixtures ----------------------------------------------------

val playlistCurrentUser =
    CurrentUser(
        id = "playlist-current-user",
        uid = "100001",
        username = "playlist_owner",
        displayName = "歌单创建人",
        bio = "",
        gender = "secret",
        email = null,
        emailPublic = false,
        phoneNumber = null,
        phoneNumberPublic = false,
        avatarUrl = null,
        defaultAvatarKey = "green-2",
        isSuperuser = false,
        createdAt = null,
    )

val playlistRoom =
    PublicRoom(
        id = "room-1",
        rid = "R10001",
        name = "音乐房间",
        avatarUrl = null,
        defaultAvatarKey = "room-2",
        visibility = "public",
        joinPolicy = "open",
        memberCount = 8,
        liveParticipantCount = 2,
        joined = true,
        joinState = "joined",
    )

val roomPlaylist =
    PersonalMusicPlaylist(id = "room-list", name = "房间收藏", description = "", revision = 1, itemCount = 1, createdAt = null, updatedAt = null)

val roomPlaylistItem =
    PersonalMusicPlaylistItem(
        id = "room-list-item",
        playlistId = "room-list",
        trackId = "track-1",
        source = "netease",
        title = "房间歌曲",
        artists = listOf("Artist"),
        durationMs = 180000,
        sortOrder = 0,
        createdAt = null,
    )

val roomPlaylistSource = MusicBoxActiveSource(type = MusicBoxActiveSourceType.roomPlaylist, id = "room-list", name = "房间收藏")

fun musicTrack(
    id: String,
    title: String = "Song",
    artist: String = "歌手",
    source: String = "netease",
    trackId: String? = null,
    status: MusicBoxQueueItemStatus = MusicBoxQueueItemStatus.ready,
    canPlayNow: Boolean = false,
    canRemove: Boolean = true,
    requestedBy: MusicBoxRequester? = null,
    playable: Boolean? = null,
): MusicBoxQueueItem =
    MusicBoxQueueItem(
        id = id,
        source = source,
        trackId = trackId ?: "track-$id",
        title = title,
        artist = artist,
        durationMs = 180000,
        status = status,
        fileSizeBytes = 0,
        error = "",
        addedByUserId = "user",
        createdAt = null,
        canPlayNow = canPlayNow,
        canRemove = canRemove,
        requestedBy = requestedBy,
        playable = playable,
    )

fun musicState(
    playbackState: MusicBoxPlaybackState,
    positionMs: Int,
    currentItemId: String = "a",
    queue: List<MusicBoxQueueItem>? = null,
    temporaryQueue: List<MusicBoxQueueItem>? = null,
    activeSource: MusicBoxActiveSource = MusicBoxActiveSource(),
    capabilities: MusicBoxCapabilities = MusicBoxCapabilities(),
    revision: Int = 0,
    hasRevision: Boolean = false,
    mode: MusicBoxPlaybackMode = MusicBoxPlaybackMode.sequential,
    canPrevious: Boolean = false,
    canNext: Boolean = false,
): MusicBoxState {
    val activeQueue =
        queue
            ?: listOf(
                MusicBoxQueueItem(
                    id = currentItemId,
                    source = "netease",
                    trackId = "track-$currentItemId",
                    title = "Song",
                    artist = "",
                    durationMs = 200000,
                    status = MusicBoxQueueItemStatus.ready,
                    fileSizeBytes = 0,
                    error = "",
                    addedByUserId = "user",
                    createdAt = null,
                ),
            )
    return MusicBoxState(
        enabled = true,
        playback =
            MusicBoxPlayback(
                state = playbackState,
                currentItemId = currentItemId,
                positionMs = positionMs,
                volume = 100,
                updatedAt = null,
                mode = mode,
                canPrevious = canPrevious,
                canNext = canNext,
                capabilities = capabilities,
            ),
        queue = activeQueue,
        usage = MusicBoxUsage(usedBytes = 0, limitBytes = 0),
        revision = revision,
        hasRevision = hasRevision,
        activeSource = activeSource,
        temporaryQueuedCount = temporaryQueue?.size ?: 0,
        temporaryQueue = temporaryQueue ?: emptyList(),
    )
}

/** Dart's `noSuchMethod` fake base: every method not overridden throws. */
inline fun <reified T : Any> unimplementedFake(): T {
    val name = T::class.java.simpleName
    return java.lang.reflect.Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.getOrNull(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "Unimplemented $name"
            else -> throw NotImplementedError("$name.${method.name} is not implemented by this fake")
        }
    } as T
}

open class MusicBoxApiFake(val state: MusicBoxState) : GangApi by unimplementedFake() {
    var action: String? = null
    var itemId: String? = null
    var commandId: String? = null
    var expectedRevision: Int? = null
    var activatedSourceType: MusicBoxActiveSourceType? = null
    var activatedPlaylistId: String? = null
    var activatedStartItemId: String? = null

    override suspend fun controlMusicBox(
        roomId: String,
        action: String,
        itemId: String?,
        mode: String?,
        commandId: String?,
        expectedRevision: Int?,
    ): MusicBoxState {
        this.action = action
        this.itemId = itemId
        this.commandId = commandId
        this.expectedRevision = expectedRevision
        return state
    }

    override suspend fun activateMusicBoxPlaylist(
        roomId: String,
        sourceType: MusicBoxActiveSourceType,
        playlistId: String?,
        startPlay: Boolean,
        startItemId: String?,
    ): MusicBoxState {
        activatedSourceType = sourceType
        activatedPlaylistId = playlistId
        activatedStartItemId = startItemId
        return state
    }
}

class RoomPlaylistApiFake(
    state: MusicBoxState,
    var playlist: PersonalMusicPlaylist,
    var items: List<PersonalMusicPlaylistItem>,
    var roomPlaylists: List<PersonalMusicPlaylist>? = null,
    var personalPlaylists: List<PersonalMusicPlaylist> = emptyList(),
) : MusicBoxApiFake(state),
    RoomMusicPlaylistApi by unimplementedFake(),
    PersonalMusicPlaylistApi by unimplementedFake() {
    var roomListCalls = 0
    var personalListCalls = 0
    var roomPlaylistLoads = 0

    override suspend fun listRoomMusicPlaylists(roomId: String, page: Int, pageSize: Int): PersonalMusicPlaylistPage {
        roomListCalls += 1
        val playlists = roomPlaylists ?: listOf(playlist)
        return PersonalMusicPlaylistPage(
            playlists = playlists,
            page = page,
            pageSize = pageSize,
            total = playlists.size,
            hasMore = false,
            maxPlaylists = 50,
            maxPlaylistItems = 500,
        )
    }

    override suspend fun getRoomMusicPlaylist(
        roomId: String,
        playlistId: String,
        page: Int,
        pageSize: Int,
        keyword: String?,
        source: String?,
    ): PersonalMusicPlaylistItemsPage {
        roomPlaylistLoads += 1
        return PersonalMusicPlaylistItemsPage(playlist = playlist, items = items, page = page, pageSize = pageSize, total = items.size, hasMore = false)
    }

    override suspend fun listPersonalMusicPlaylists(page: Int, pageSize: Int): PersonalMusicPlaylistPage {
        personalListCalls += 1
        return PersonalMusicPlaylistPage(
            playlists = personalPlaylists,
            page = page,
            pageSize = pageSize,
            total = personalPlaylists.size,
            hasMore = false,
            maxPlaylists = 50,
            maxPlaylistItems = 500,
        )
    }
}

// --- Finders ----------------------------------------------------------------

fun hasIcon(icon: ImageVector): SemanticsMatcher = SemanticsMatcher.expectValue(IconSemanticsKey, icon)

fun hasLiveOpacity(opacity: Float): SemanticsMatcher = SemanticsMatcher.expectValue(LiveOpacityKey, opacity)

/** The node tagged [tag] (unmerged tree). */
fun ComposeContentTestRule.tag(tag: String): SemanticsNodeInteraction = onNode(hasTestTag(tag), useUnmergedTree = true)

/** Descendants of the node tagged [ancestorTag] matching [matcher] (unmerged tree). */
fun ComposeContentTestRule.within(ancestorTag: String, matcher: SemanticsMatcher) =
    onAllNodes(matcher and hasAnyAncestor(hasTestTag(ancestorTag)), useUnmergedTree = true)

fun ComposeContentTestRule.countOf(matcher: SemanticsMatcher): Int = onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

fun ComposeContentTestRule.tagCount(tag: String): Int = countOf(hasTestTag(tag))

/** A node's bounds in root dp. */
fun ComposeContentTestRule.rectOf(tag: String): DpRect = this.tag(tag).dpBounds(density.density)

val DpRect.widthDp: Dp get() = right - left
val DpRect.heightDp: Dp get() = bottom - top

/** The text parts of a node. */
fun SemanticsNodeInteraction.texts(): List<String> =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()
