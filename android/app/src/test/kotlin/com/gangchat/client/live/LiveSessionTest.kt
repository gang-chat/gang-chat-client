package com.gangchat.client.live

import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.complete
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.waitAll
import com.gangchat.client.platform.webrtc.AndroidAudioAttributesContentType
import com.gangchat.client.platform.webrtc.AndroidAudioAttributesUsageType
import com.gangchat.client.platform.webrtc.AndroidAudioMode
import com.gangchat.client.platform.webrtc.AndroidAudioStreamType
import com.gangchat.client.testing.GangTestRule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Port of test/unit/live/live_session_test.dart.
 *
 * Not ported (desktop picker only; Android shares through the system
 * MediaProjection prompt and has no source picker): "screen-share source
 * picker keeps screens without thumbnails", "... filters known invisible
 * overlay windows", "... keeps windows without thumbnails", "... keeps
 * non-overlay system windows", "... keeps same raw id across types" and
 * "screen-share thumbnail updates are cached for reopened picker".
 */
class LiveSessionTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `camera flip capability distinguishes unknown and single-camera`() {
        assertNull(cameraFlipAvailabilityFromVideoInputCount(0))
        assertEquals(false, cameraFlipAvailabilityFromVideoInputCount(1))
        assertEquals(true, cameraFlipAvailabilityFromVideoInputCount(2))
    }

    @Test
    fun `Android music box switches playback to media mode and volume`() {
        val media = androidLiveAudioConfiguration(musicBoxActive = true)
        assertEquals(AndroidAudioMode.normal, media.androidAudioMode)
        assertEquals(AndroidAudioStreamType.music, media.androidAudioStreamType)
        assertEquals(AndroidAudioAttributesUsageType.media, media.androidAudioAttributesUsageType)
        assertEquals(AndroidAudioAttributesContentType.music, media.androidAudioAttributesContentType)
        assertEquals(true, media.forceHandleAudioRouting)

        val voice = androidLiveAudioConfiguration(musicBoxActive = false)
        assertEquals(AndroidAudioMode.inCommunication, voice.androidAudioMode)
        assertEquals(AndroidAudioStreamType.voiceCall, voice.androidAudioStreamType)
        assertEquals(AndroidAudioAttributesUsageType.voiceCommunication, voice.androidAudioAttributesUsageType)
    }

    @Test
    fun `participant removal maps to the kicked announcement kind`() {
        assertEquals(
            LiveParticipantDepartureKind.removed,
            liveParticipantDepartureKind(LkDisconnectReason.participantRemoved),
        )
        assertEquals(
            LiveParticipantDepartureKind.left,
            liveParticipantDepartureKind(LkDisconnectReason.clientInitiated),
        )
        assertEquals(LiveParticipantDepartureKind.left, liveParticipantDepartureKind(null))
    }

    @Test
    fun `missing microphone publication is unknown during participant reconnect`() {
        assertNull(liveParticipantMicPublicationMuted(FakeLiveParticipant(microphonePublication = null)))
        assertEquals(
            true,
            liveParticipantMicPublicationMuted(
                FakeLiveParticipant(microphonePublication = FakeTrackPublication(muted = true)),
            ),
        )
        assertEquals(
            false,
            liveParticipantMicPublicationMuted(
                FakeLiveParticipant(microphonePublication = FakeTrackPublication(muted = false)),
            ),
        )
    }

    @Test
    fun `media reconnect clears stale speaking highlights immediately`() = gang.runTest {
        val session = LiveSession(inputRebinderFactory = { null }, outputRebinderFactory = { null })
        try {
            val participant = FakeLiveParticipant(identity = "speaker", microphonePublication = null)

            session.debugHandleRoomEvent(LkActiveSpeakersChangedEvent(speakers = listOf(participant)))
            assertEquals(setOf("speaker"), session.speakingIdentities)

            session.debugHandleRoomEvent(LkRoomReconnectingEvent())

            assertTrue(session.speakingIdentities.isEmpty())
            assertTrue(session.micMutedByIdentity.isEmpty())
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `only recoverable room disconnects request an app-level reconnect`() = gang.runTest {
        val session = LiveSession(inputRebinderFactory = { null }, outputRebinderFactory = { null })
        try {
            var reconnects = 0
            var removals = 0
            session.onUnexpectedlyDisconnected = { reconnects += 1 }
            session.onForciblyRemoved = { removals += 1 }

            session.debugHandleRoomEvent(
                LkRoomDisconnectedEvent(reason = LkDisconnectReason.reconnectAttemptsExceeded),
            )
            session.debugHandleRoomEvent(LkRoomDisconnectedEvent(reason = LkDisconnectReason.duplicateIdentity))
            session.debugHandleRoomEvent(LkRoomDisconnectedEvent(reason = LkDisconnectReason.participantRemoved))

            assertEquals(1, reconnects)
            assertEquals(1, removals)
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `presence cues ignore hidden audio participants`() {
        assertTrue(isLivePresenceSoundParticipantIdentity("user-2"))
        assertFalse(isLivePresenceSoundParticipantIdentity("user-2--screen-audio"))
        assertFalse(isLivePresenceSoundParticipantIdentity("__musicbox__"))
        assertFalse(isLivePresenceSoundParticipantIdentity(""))
    }

    @Test
    fun `reconnect presence diff ignores users restored on both sides`() {
        val changes =
            liveReconnectPresenceChanges(
                before = setOf("stayed", "left"),
                after = setOf("stayed", "joined"),
            )

        assertEquals(setOf("left"), changes.left)
        assertEquals(setOf("joined"), changes.joined)
    }

    @Test
    fun `reconnect republishes only an established permitted microphone`() {
        assertTrue(
            shouldRepublishLocalMicrophoneAfterReconnect(
                resumedSignalConnection = true,
                hadPublicationBeforeReconnect = true,
                canPublish = true,
            ),
        )
        assertFalse(
            shouldRepublishLocalMicrophoneAfterReconnect(
                resumedSignalConnection = true,
                hadPublicationBeforeReconnect = false,
                canPublish = true,
            ),
        )
        assertFalse(
            shouldRepublishLocalMicrophoneAfterReconnect(
                resumedSignalConnection = true,
                hadPublicationBeforeReconnect = true,
                canPublish = false,
            ),
        )
        assertFalse(
            shouldRepublishLocalMicrophoneAfterReconnect(
                resumedSignalConnection = false,
                hadPublicationBeforeReconnect = true,
                canPublish = true,
            ),
        )
    }

    @Test
    fun `reconnect microphone recovery retries after native teardown races`() = gang.runTest {
        var attempts = 0
        val waits = mutableListOf<Duration>()

        val recovered =
            retryLiveReconnectMicrophoneRecovery(
                attempt = {
                    attempts += 1
                    attempts == 3
                },
                isCurrent = { true },
                retryDelays = listOf(Duration.ZERO, 10.milliseconds, 20.milliseconds),
                wait = { delay -> waits.add(delay) },
            )

        assertTrue(recovered)
        assertEquals(3, attempts)
        assertEquals(listOf(10.milliseconds, 20.milliseconds), waits)
    }

    @Test
    fun `reconnect microphone recovery stops when its room is superseded`() = gang.runTest {
        var current = true
        var attempts = 0

        val recovered =
            retryLiveReconnectMicrophoneRecovery(
                attempt = {
                    attempts += 1
                    current = false
                    false
                },
                isCurrent = { current },
                retryDelays = listOf(Duration.ZERO, Duration.ZERO),
            )

        assertFalse(recovered)
        assertEquals(1, attempts)
    }

    @Test
    fun `latest subscription reconciliation runs last`() = gang.runTest {
        val reconciler = LatestLiveSubscriptionReconciler()
        val firstStarted = Completer<Unit>()
        val releaseFirst = Completer<Unit>()
        val applied = mutableListOf<String>()

        val first =
            reconciler.schedule { isCurrent ->
                firstStarted.complete()
                releaseFirst.future.await()
                if (isCurrent()) applied.add("first")
            }
        firstStarted.future.await()

        val second =
            reconciler.schedule { isCurrent ->
                if (isCurrent()) applied.add("second")
            }
        releaseFirst.complete()
        waitAll(listOf(first, second))

        assertEquals(listOf("second"), applied)
    }

    @Test
    fun `invalidated subscription reconciliation does not start`() = gang.runTest {
        val reconciler = LatestLiveSubscriptionReconciler()
        val blocker = Completer<Unit>()
        val blockingTask = reconciler.schedule { blocker.future.await() }
        var ranInvalidatedTask = false
        val invalidatedTask = reconciler.schedule { ranInvalidatedTask = true }

        reconciler.invalidate()
        blocker.complete()
        waitAll(listOf(blockingTask, invalidatedTask))

        assertFalse(ranInvalidatedTask)
    }

    @Test
    fun `screen-share requests audio for desktop and Android capture`() {
        assertTrue(
            shouldRequestScreenShareAudio(
                sourceId = "screen-primary",
                isDesktopSourcePickerPlatform = true,
                isWindowsDesktop = false,
                isAndroidPlatform = false,
            ),
        )
        assertTrue(
            shouldRequestScreenShareAudio(
                sourceId = null,
                isDesktopSourcePickerPlatform = true,
                isWindowsDesktop = false,
                isAndroidPlatform = false,
            ),
        )
        assertFalse(
            shouldRequestScreenShareAudio(
                sourceId = "screen-primary",
                isDesktopSourcePickerPlatform = false,
                isWindowsDesktop = false,
                isAndroidPlatform = false,
            ),
        )
        assertTrue(
            shouldRequestScreenShareAudio(
                sourceId = null,
                isDesktopSourcePickerPlatform = false,
                isWindowsDesktop = false,
                isAndroidPlatform = true,
            ),
        )
    }

    @Test
    fun `screen-share requests aux audio on Windows desktop`() {
        assertTrue(
            shouldRequestScreenShareAudio(
                sourceId = "screen-primary",
                isDesktopSourcePickerPlatform = true,
                isWindowsDesktop = true,
                isAndroidPlatform = false,
            ),
        )
    }

    @Test
    fun `session starts and stops the combined audio-device rebinder`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var resolutions = 0

        val session =
            LiveSession(
                audioDeviceRebinderFactory = {
                    AudioDeviceRebinder(
                        deviceChanges = changes.stream,
                        resolvePreferredDevices = {
                            resolutions += 1
                            PreferredLiveAudioDevices()
                        },
                        rebindInput = { },
                        selectOutput = { },
                        onOutputRebound = { },
                        debounce = 10.milliseconds,
                    )
                },
            )
        try {
            session.debugStartAudioDeviceRebinder()
            changes.add(Unit)
            delayed(30.milliseconds)
            assertEquals(1, resolutions)

            session.debugStopAudioDeviceRebinder()
            changes.add(Unit)
            delayed(30.milliseconds)
            assertEquals(1, resolutions)
        } finally {
            session.dispose()
            changes.close()
        }
    }

    @Test
    fun `session starts and stops the Windows camera reconciler`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var refreshes = 0

        val session =
            LiveSession(
                audioDeviceRebinderFactory = { null },
                cameraDeviceReconcilerFactory = {
                    CameraDeviceReconciler(
                        deviceChanges = changes.stream,
                        refreshDevices = { refreshes += 1 },
                        debounce = 10.milliseconds,
                    )
                },
            )
        try {
            session.debugStartAudioDeviceRebinder()
            changes.add(Unit)
            delayed(30.milliseconds)
            assertEquals(1, refreshes)

            session.debugStopAudioDeviceRebinder()
            changes.add(Unit)
            delayed(30.milliseconds)
            assertEquals(1, refreshes)
        } finally {
            session.dispose()
            changes.close()
        }
    }

    @Test
    fun `session starts the output rebinder while connected`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        val selected = mutableListOf<String>()

        var built: AudioOutputRebinder? = null
        val session =
            LiveSession(
                outputRebinderFactory = {
                    AudioOutputRebinder(
                        deviceChanges = changes.stream,
                        currentOutputDeviceId = { "speaker_1" },
                        selectOutput = { id -> selected.add(id) },
                        onRebound = { },
                        debounce = 10.milliseconds,
                    ).also { built = it }
                },
            )
        try {
            session.debugStartOutputRebinder()
            changes.add(Unit)
            delayed(30.milliseconds)

            // The rebinder built by the factory is live and reacting to device flips.
            assertNotNull(built)
            assertEquals(listOf("speaker_1"), selected)
        } finally {
            session.dispose()
            changes.close()
        }
    }

    @Test
    fun `session starts the input rebinder while connected`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        val rebound = mutableListOf<String?>()

        var built: AudioInputRebinder? = null
        val session =
            LiveSession(
                inputRebinderFactory = {
                    AudioInputRebinder(
                        deviceChanges = changes.stream,
                        currentInputDeviceId = { "mic_1" },
                        rebindInput = { id -> rebound.add(id) },
                        debounce = 10.milliseconds,
                    ).also { built = it }
                },
                outputRebinderFactory = { null },
            )
        try {
            session.debugStartInputRebinder()
            changes.add(Unit)
            delayed(30.milliseconds)

            assertNotNull(built)
            assertEquals(listOf<String?>("mic_1"), rebound)
        } finally {
            session.dispose()
            changes.close()
        }
    }

    @Test
    fun `session stops the output rebinder so flips stop rebinding`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var selects = 0

        val session =
            LiveSession(
                outputRebinderFactory = {
                    AudioOutputRebinder(
                        deviceChanges = changes.stream,
                        currentOutputDeviceId = { "speaker_1" },
                        selectOutput = { selects += 1 },
                        onRebound = { },
                        debounce = 10.milliseconds,
                    )
                },
            )
        try {
            session.debugStartOutputRebinder()
            session.debugStopOutputRebinder()
            changes.add(Unit)
            delayed(30.milliseconds)

            assertEquals(0, selects)
        } finally {
            session.dispose()
            changes.close()
        }
    }

    @Test
    fun `session stops the input rebinder so flips stop rebinding`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var rebounds = 0

        val session =
            LiveSession(
                inputRebinderFactory = {
                    AudioInputRebinder(
                        deviceChanges = changes.stream,
                        currentInputDeviceId = { "mic_1" },
                        rebindInput = { rebounds += 1 },
                        debounce = 10.milliseconds,
                    )
                },
                outputRebinderFactory = { null },
            )
        try {
            session.debugStartInputRebinder()
            session.debugStopInputRebinder()
            changes.add(Unit)
            delayed(30.milliseconds)

            assertEquals(0, rebounds)
        } finally {
            session.dispose()
            changes.close()
        }
    }

    @Test
    fun `a null factory disables output rebinding (non-macOS)`() = gang.runTest {
        val session = LiveSession(outputRebinderFactory = { null })
        try {
            // Should be a no-op rather than throwing when there's nothing to rebind.
            session.debugStartOutputRebinder()
            session.debugStopOutputRebinder()
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `a null factory disables input rebinding (non-desktop)`() = gang.runTest {
        val session = LiveSession(inputRebinderFactory = { null }, outputRebinderFactory = { null })
        try {
            session.debugStartInputRebinder()
            session.debugStopInputRebinder()
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `publish permission updates ignore headphone-only permission events`() {
        assertFalse(
            shouldApplyLivePublishPermissionUpdate(
                currentCanPublish = true,
                oldCanPublish = true,
                nextCanPublish = true,
            ),
        )
        assertTrue(
            shouldApplyLivePublishPermissionUpdate(
                currentCanPublish = true,
                oldCanPublish = true,
                nextCanPublish = false,
            ),
        )
        assertTrue(
            shouldApplyLivePublishPermissionUpdate(
                currentCanPublish = false,
                oldCanPublish = false,
                nextCanPublish = true,
            ),
        )
        assertFalse(
            shouldApplyLivePublishPermissionUpdate(
                currentCanPublish = true,
                oldCanPublish = false,
                nextCanPublish = false,
            ),
        )
    }
}

internal class FakeLiveParticipant(
    override val identity: String = "participant",
    private val microphonePublication: LkTrackPublication?,
) : LkParticipant {
    override val audioTrackPublications: List<LkTrackPublication> get() = listOfNotNull(microphonePublication)
    override val videoTrackPublications: List<LkTrackPublication> get() = emptyList()

    override fun getTrackPublicationBySource(source: LkTrackSource): LkTrackPublication? =
        if (source == LkTrackSource.microphone) microphonePublication else null
}

internal class FakeTrackPublication(
    override val muted: Boolean,
    override val sid: String = "TR_fake",
    override val source: LkTrackSource = LkTrackSource.microphone,
    override val track: LkTrack? = null,
) : LkTrackPublication
