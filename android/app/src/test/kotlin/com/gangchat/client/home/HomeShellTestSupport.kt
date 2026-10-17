package com.gangchat.client.home

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.AuthenticatedAppContext
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.CloseBehaviorStore
import com.gangchat.client.app.LanguagePreferenceStore
import com.gangchat.client.app.LivePresenceAnnouncement
import com.gangchat.client.app.LivePresenceSpeechPlayer
import com.gangchat.client.app.LiveSessionController
import com.gangchat.client.app.RealtimeConnectionStatus
import com.gangchat.client.app.RealtimeEvent
import com.gangchat.client.app.RealtimeService
import com.gangchat.client.app.ServerClock
import com.gangchat.client.app.StoredAudioDevices
import com.gangchat.client.auth.AuthSession
import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.live.LiveParticipantDepartureKind
import com.gangchat.client.live.LivePresenceSound
import com.gangchat.client.live.LivePresenceSoundPlayer
import com.gangchat.client.live.LiveSession
import com.gangchat.client.live.LiveVideoTrack
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.settings.MemorySettingsCloseBehaviorStore
import com.gangchat.client.settings.MemorySettingsLanguageStore
import com.gangchat.client.shell.LocalAudioDeviceStore
import com.gangchat.client.shell.AndroidPushRegistration
import com.gangchat.client.shell.AndroidSystemService
import com.gangchat.client.shell.AndroidTaskRemovalRequest
import com.gangchat.client.shell.MessageNotificationSoundPlayer
import com.gangchat.client.ui.AppNotificationHost
import com.gangchat.client.ui.UiTheme
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.LocalMediaQuery
import com.gangchat.client.widgets.RootNavigator
import com.gangchat.client.widgets.WindowMediaQuery
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration
import java.time.Duration as JavaDuration

// The home shell test fixtures of test/widget/shell/gang_app_shell_test.dart:
// `_homeTestAppContext`, the realtime / live / audio fakes and the recording
// players. The API fake is [HomeShellTestApi].

/** Dart's `_homeTestAppContext` (the API's options live on [api]). */
fun homeTestAppContext(
    api: HomeShellTestApi = HomeShellTestApi(),
    onLogout: (suspend () -> Unit)? = null,
    onExitSessionForAppExit: (suspend () -> Unit)? = null,
    currentUserIsSuperuser: Boolean = false,
): AuthenticatedAppContext {
    val user =
        CurrentUser(
            id = "user-1",
            uid = "uid-1",
            username = "kai",
            displayName = "Kai",
            bio = "",
            gender = "secret",
            email = "kai@example.com",
            emailPublic = false,
            phoneNumber = null,
            phoneNumberPublic = false,
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
            isSuperuser = currentUserIsSuperuser,
            createdAt = DateTime.utc(2026),
        )
    return AuthenticatedAppContext(
        session =
            AuthSession(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                accessTokenExpiresAt = DateTime.utc(2026, 1, 1, 1),
                user = user,
            ),
        apiBaseUrl = "http://localhost:3000",
        accessTokenProvider = { _ -> "access-token" },
        serverClock = ServerClock(localNow = { DateTime.utc(2026, 6, 12, 2) }),
        logout = onLogout ?: {},
        exitSessionForAppExit = onExitSessionForAppExit ?: {},
        api = api.client,
    )
}

/** Dart's `_NoopRealtimeService`. */
class NoopRealtimeService : RealtimeService {
    override var onReconnect: (() -> Unit)? = null
    override val events: Stream<RealtimeEvent> = Stream.empty()
    override val status: RealtimeConnectionStatus get() = RealtimeConnectionStatus.offline
    override val statusChanges: Stream<RealtimeConnectionStatus> = Stream.empty()

    override suspend fun start() {}

    override suspend fun stop() {}

    override fun dispose() {}
}

/** Dart's `_RecordingRealtimeService`. */
class RecordingRealtimeService(private val lifecycleEvents: MutableList<String>) : RealtimeService {
    override var onReconnect: (() -> Unit)? = null
    override val events: Stream<RealtimeEvent> = Stream.empty()
    override val status: RealtimeConnectionStatus get() = RealtimeConnectionStatus.offline
    override val statusChanges: Stream<RealtimeConnectionStatus> = Stream.empty()

    override suspend fun start() {}

    override suspend fun stop() {
        lifecycleEvents.add("realtime-stop")
    }

    override fun dispose() {}
}

/** Dart's `_FakeRealtimeService`. */
class FakeHomeRealtimeService : RealtimeService {
    private val _controller = StreamController.broadcast<RealtimeEvent>()
    private val _statusController = StreamController.broadcast<RealtimeConnectionStatus>()
    private var _status = RealtimeConnectionStatus.connected

    override var onReconnect: (() -> Unit)? = null
    override val events: Stream<RealtimeEvent> get() = _controller.stream
    override val status: RealtimeConnectionStatus get() = _status
    override val statusChanges: Stream<RealtimeConnectionStatus> get() = _statusController.stream

    override suspend fun start() {}

    override suspend fun stop() {}

    fun add(event: RealtimeEvent) = _controller.add(event)

    fun add(type: String, data: Map<String, Any?>) = _controller.add(RealtimeEvent(type = type, data = data))

    fun setStatus(status: RealtimeConnectionStatus) {
        _status = status
        _statusController.add(status)
    }

    fun emitReconnect() = onReconnect?.invoke()

    override fun dispose() {
        _controller.close()
        _statusController.close()
    }
}

/**
 * Dart's `_FakeAudioDeviceStore`. The writes Dart left to the throwing base
 * class throw an ordinary exception here (Kotlin's `NotImplementedError` is
 * an `Error` the app's `catch (Exception)` handlers would not see, where
 * Dart's `catch (_)` caught the `UnimplementedError`).
 */
class FakeHomeAudioDeviceStore(
    val inputVolume: Double = 0.35,
    val outputVolume: Double = 0.75,
) : AudioDeviceStore() {
    override suspend fun read(): StoredAudioDevices = StoredAudioDevices(inputVolume = inputVolume, outputVolume = outputVolume)

    override suspend fun writeInputDeviceId(deviceId: String) {}

    override suspend fun writeOutputDeviceId(deviceId: String) {}

    override suspend fun writeInputVolume(volume: Double) {}

    override suspend fun writeOutputVolume(volume: Double) {}

    override suspend fun writeMusicBoxVolume(volume: Double) {
        throw UnsupportedOperationException("AudioDeviceStore.writeMusicBoxVolume must be implemented.")
    }

    override suspend fun writeScreenShareVolume(volume: Double) {
        throw UnsupportedOperationException("AudioDeviceStore.writeScreenShareVolume must be implemented.")
    }

    override suspend fun writeScreenShareMaxHeight(height: Int) {}

    override suspend fun writeScreenShareFrameRate(frameRate: Int) {}
}

/** Dart's `_RecordingLivePresenceSoundPlayer`. */
class RecordingLivePresenceSoundPlayer : LivePresenceSoundPlayer {
    val sounds = mutableListOf<LivePresenceSound>()
    val volumes = mutableListOf<Double>()
    var nextPlaybackCompletion: Completer<Unit>? = null

    override suspend fun play(sound: LivePresenceSound, volume: Double) {
        sounds.add(sound)
        volumes.add(volume)
        val completion = nextPlaybackCompletion
        nextPlaybackCompletion = null
        completion?.future?.await()
    }

    override suspend fun dispose() {}
}

/** Dart's `_RecordingLivePresenceSpeechPlayer`. */
class RecordingLivePresenceSpeechPlayer : LivePresenceSpeechPlayer {
    val announcements = mutableListOf<LivePresenceAnnouncement>()
    val volumes = mutableListOf<Double>()

    override suspend fun speak(announcement: LivePresenceAnnouncement, volume: Double) {
        announcements.add(announcement)
        volumes.add(volume)
    }

    override suspend fun dispose() {}
}

/** Dart's `_RecordingMessageNotificationSoundPlayer`. */
class RecordingMessageNotificationSoundPlayer : MessageNotificationSoundPlayer {
    val volumes = mutableListOf<Double>()
    var disposed = false

    override suspend fun play(volume: Double) {
        volumes.add(volume)
    }

    override suspend fun dispose() {
        disposed = true
    }
}

/** Dart's `_FakeLiveSessionController` (Android has no screen source picker to fake). */
class FakeHomeLiveSessionController(
    session: LiveSession,
    audioDeviceStore: AudioDeviceStore = FakeHomeAudioDeviceStore(),
) : LiveSessionController(
        apiBaseUrl = "http://localhost:3000",
        audioDeviceStore = audioDeviceStore,
        session = session,
        audioDeviceRestorer = { _ -> null },
    )

/** Dart's `_FakeLiveSession`. */
class FakeHomeLiveSession(private val failMicUnmute: Boolean = false) : LiveSession() {
    var connectAttempts = 0
    var disconnects = 0
    private var _connected = false
    private var _fakeRoomName: String? = null
    private var _fakeLocalMicMuted = true
    val inputVolumes = mutableListOf<Double>()
    val outputVolumes = mutableListOf<Double>()
    val participantVoiceVolumeWrites = mutableListOf<String>()
    val screenShareVolumes = mutableListOf<Double>()
    val micMutes = mutableListOf<Boolean>()
    val outputMutes = mutableListOf<Boolean>()
    val cameraEnables = mutableListOf<Boolean>()
    val screenShareEnables = mutableListOf<Boolean>()
    val screenShareSourceIds = mutableListOf<String?>()
    var testVideoTracks: List<LiveVideoTrack> = emptyList()

    override val videoTracks: List<LiveVideoTrack> get() = testVideoTracks

    fun emitParticipantJoined() = onParticipantJoined?.invoke("user-2")

    fun emitParticipantLeft(removed: Boolean = false) =
        onParticipantLeft?.invoke(
            "user-2",
            if (removed) LiveParticipantDepartureKind.removed else LiveParticipantDepartureKind.left,
        )

    fun emitUnexpectedDisconnect() {
        _connected = false
        onUnexpectedlyDisconnected?.invoke()
    }

    fun emitChange() = notifyListeners()

    override suspend fun connect(url: String, token: String, roomName: String, micMuted: Boolean) {
        connectAttempts += 1
        _connected = true
        _fakeRoomName = roomName
        _fakeLocalMicMuted = micMuted
    }

    override suspend fun disconnect() {
        disconnects += 1
        _connected = false
        _fakeRoomName = null
    }

    override val isConnected: Boolean get() = _connected

    override val roomName: String? get() = _fakeRoomName

    override fun isAttachedToRoom(roomName: String): Boolean = _connected && _fakeRoomName == roomName

    override val localMicMuted: Boolean get() = _fakeLocalMicMuted

    override suspend fun setMicMuted(muted: Boolean) {
        micMutes.add(muted)
        _fakeLocalMicMuted = if (!muted && failMicUnmute) true else muted
    }

    override suspend fun setOutputMuted(muted: Boolean) {
        outputMutes.add(muted)
    }

    override suspend fun setInputVolume(volume: Double) {
        inputVolumes.add(volume)
        super.setInputVolume(volume)
    }

    override suspend fun setOutputVolume(volume: Double) {
        outputVolumes.add(volume)
        super.setOutputVolume(volume)
    }

    override suspend fun setParticipantVoiceVolume(userId: String, volume: Double) {
        participantVoiceVolumeWrites.add("$userId:${"%.2f".format(volume)}")
        super.setParticipantVoiceVolume(userId, volume)
    }

    override suspend fun setScreenShareVolume(volume: Double) {
        screenShareVolumes.add(volume)
        super.setScreenShareVolume(volume)
    }

    override suspend fun setCameraEnabled(enabled: Boolean): Boolean {
        cameraEnables.add(enabled)
        return enabled
    }

    override suspend fun setScreenShareEnabled(enabled: Boolean, sourceId: String?): Boolean {
        screenShareEnables.add(enabled)
        screenShareSourceIds.add(sourceId)
        return enabled
    }
}

/**
 * The Android system services under test. [supported] false is the Dart
 * suite's desktop host (no Android services); true is Dart's
 * `_TaskRemovalAndroidSystemService` and friends, recording what the shell
 * asks of the system in [events].
 */
open class FakeHomeAndroidSystemService(
    private val supported: Boolean = false,
    var initialNotificationRoomId: String? = null,
) : AndroidSystemService() {
    val events = mutableListOf<String>()
    private val _selectedRooms = StreamController.broadcast<String>(sync = true)
    private val _taskRemovals = StreamController.broadcast<AndroidTaskRemovalRequest>(sync = true)

    override val isSupported: Boolean get() = supported

    override val selectedNotificationRoomIds: Stream<String> get() = _selectedRooms.stream

    override val taskRemovalRequests: Stream<AndroidTaskRemovalRequest> get() = _taskRemovals.stream

    override val pushRegistrationChanges: Stream<AndroidPushRegistration> get() = Stream.empty()

    override suspend fun pushRegistration(): AndroidPushRegistration? = null

    override suspend fun takeInitialNotificationRoomId(): String? {
        val roomId = initialNotificationRoomId
        initialNotificationRoomId = null
        return roomId
    }

    override suspend fun notificationPreferenceEnabled(): Boolean = false

    override suspend fun notificationsEnabled(): Boolean = false

    override suspend fun requestBluetoothConnectPermission(): Boolean = true

    override suspend fun enterBackground() {
        events.add("enter-background")
    }

    override suspend fun enterForeground() {
        events.add("enter-foreground")
    }

    override suspend fun showRoomMessage(
        roomId: String,
        roomName: String,
        sender: String,
        body: String,
        unreadCount: Int,
        messageId: String?,
    ) {
        events.add("show-room-message:$roomId:$roomName:$sender:$body:$unreadCount")
    }

    override suspend fun cancelRoomNotification(roomId: String) {
        events.add("cancel-room-notification:$roomId")
    }

    override suspend fun syncBadge(unreadCount: Int) {
        events.add("badge:$unreadCount")
    }

    override suspend fun clearMessageNotifications() {
        events.add("clear-message-notifications")
    }

    override suspend fun speak(segments: List<String>, volume: Double, pause: Duration) {}

    override suspend fun disposeSpeech() {}

    /** A notification tap that opens [roomId]. */
    fun selectNotificationRoom(roomId: String) = _selectedRooms.add(roomId)

    /** Android removing the task from recents. */
    fun removeTask(): AndroidTaskRemovalRequest {
        val request = AndroidTaskRemovalRequest()
        _taskRemovals.add(request)
        return request
    }

    fun dispose() {
        _selectedRooms.close()
        _taskRemovals.close()
    }
}

/**
 * The test app root for the home shell (Dart's `MaterialApp(theme:
 * uiTheme(), home: HomePage(...))`): theme, window media query (with
 * [viewPadding] as the system insets when given), notices and the root
 * navigator. [content] is usually [HomePage].
 */
fun ComposeContentTestRule.setHomeShellContent(
    viewPadding: (() -> EdgeInsets)? = null,
    viewInsets: (() -> EdgeInsets)? = null,
    size: (() -> DpSize?)? = null,
    content: @Composable () -> Unit,
) {
    setContent {
        UiTheme {
            WindowMediaQuery {
                var media = LocalMediaQuery.current
                val padding = viewPadding?.invoke()
                if (padding != null) media = media.copy(viewPadding = padding, padding = padding)
                val insets = viewInsets?.invoke()
                if (insets != null) media = media.copy(viewInsets = insets)
                CompositionLocalProvider(LocalMediaQuery provides media) {
                    AppNotificationHost {
                        RootNavigator { _ ->
                            // Dart's `SizedBox(width:, height:)` around the page.
                            val pageSize = size?.invoke()
                            val pageModifier = if (pageSize == null) Modifier.fillMaxSize() else Modifier.requiredSize(pageSize)
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                                Box(pageModifier, propagateMinConstraints = true) { content() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [setHomeShellContent] with [HomePage] and the Dart suite's defaults. The
 * players default to recording fakes (Dart passed none and let the missing
 * audio plugin fail quietly); Android system services default to the
 * unsupported host the Dart suite ran on.
 */
fun ComposeContentTestRule.setHomePage(
    app: AuthenticatedAppContext,
    realtime: RealtimeService = NoopRealtimeService(),
    audioDeviceStore: AudioDeviceStore = LocalAudioDeviceStore(),
    liveSessionController: LiveSessionController? = null,
    livePresenceSoundPlayer: LivePresenceSoundPlayer? = RecordingLivePresenceSoundPlayer(),
    livePresenceSpeechPlayer: LivePresenceSpeechPlayer? = RecordingLivePresenceSpeechPlayer(),
    messageNotificationSoundPlayer: MessageNotificationSoundPlayer? = RecordingMessageNotificationSoundPlayer(),
    androidSystemService: AndroidSystemService = FakeHomeAndroidSystemService(),
    closeBehaviorStore: CloseBehaviorStore = MemorySettingsCloseBehaviorStore(),
    languageStore: LanguagePreferenceStore = MemorySettingsLanguageStore(),
    detectedAppUpdate: AvailableAppUpdate? = null,
    onDetectedAppUpdateShown: (() -> Unit)? = null,
    viewPadding: (() -> EdgeInsets)? = null,
    viewInsets: (() -> EdgeInsets)? = null,
    size: (() -> DpSize?)? = null,
) {
    setHomeShellContent(viewPadding = viewPadding, viewInsets = viewInsets, size = size) {
        HomePage(
            app = app,
            realtime = realtime,
            audioDeviceStore = audioDeviceStore,
            liveSessionController = liveSessionController,
            livePresenceSoundPlayer = livePresenceSoundPlayer,
            livePresenceSpeechPlayer = livePresenceSpeechPlayer,
            messageNotificationSoundPlayer = messageNotificationSoundPlayer,
            androidSystemService = androidSystemService,
            closeBehaviorStore = closeBehaviorStore,
            languageStore = languageStore,
            detectedAppUpdate = detectedAppUpdate,
            onDetectedAppUpdateShown = onDetectedAppUpdateShown,
        )
    }
}

/**
 * Dart's `tester.pump(duration)` for the shell with the composition clock
 * under manual control: runs pending work, advances [millis] on both the
 * composition clock and the main looper (where the shell's `delayed` timers
 * run), then lets two frames run.
 */
fun ComposeContentTestRule.pumpShell(millis: Long = 0) {
    waitForIdle()
    if (millis > 0) {
        mainClock.advanceTimeBy(millis)
        shadowOf(Looper.getMainLooper()).idleFor(JavaDuration.ofMillis(millis))
    }
    repeat(2) { mainClock.advanceTimeByFrame() }
    waitForIdle()
}

/**
 * Dart's `pumpAndSettle()`: a second of 100 ms frames, sleeping briefly
 * between them so work that hops to the I/O dispatcher (sticker caches)
 * lands.
 */
fun ComposeContentTestRule.settleShell(frames: Int = 10) {
    repeat(frames) {
        Thread.sleep(2)
        pumpShell(100)
    }
}

/** Pumps 25 ms frames until [condition] holds (Dart's `_pumpUntilFound`). */
fun ComposeContentTestRule.pumpShellUntil(maxPumps: Int = 80, condition: () -> Boolean) {
    for (attempt in 0 until maxPumps) {
        if (condition()) return
        Thread.sleep(2)
        pumpShell(25)
    }
    check(condition()) { "Timed out waiting for the condition." }
}

/** Whether any node (unmerged tree) matches [matcher]. */
fun ComposeContentTestRule.exists(matcher: SemanticsMatcher): Boolean =
    onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/** Dart's `find.text(text)` count (merged semantics, exact match). */
fun ComposeContentTestRule.textNodes(text: String): Int = onAllNodes(hasText(text)).fetchSemanticsNodes().size

/** The editable text inside the node tagged [tag]. */
fun ComposeContentTestRule.textFieldIn(tag: String): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

/** The text a text field node currently holds. */
fun SemanticsNodeInteraction.editableText(): String {
    val config = fetchSemanticsNode().config
    return (config.getOrNull(SemanticsProperties.EditableText) ?: config.getOrNull(SemanticsProperties.InputText))?.text ?: ""
}
