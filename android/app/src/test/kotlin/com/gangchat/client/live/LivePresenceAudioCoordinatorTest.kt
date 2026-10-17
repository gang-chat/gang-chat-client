package com.gangchat.client.live

import com.gangchat.client.app.LivePresenceAnnouncement
import com.gangchat.client.app.LivePresenceAnnouncementAction
import com.gangchat.client.app.LivePresenceSpeechPlayer
import com.gangchat.client.foundation.waitAll
import com.gangchat.client.testing.GangTestRule
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Port of test/unit/live/live_presence_audio_coordinator_test.dart. */
class LivePresenceAudioCoordinatorTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `presence audio keeps cue and speech items strictly ordered`() = gang.runTest {
        val events = mutableListOf<String>()
        val sound = RecordingSoundPlayer(events)
        val speech = RecordingSpeechPlayer(events)
        val coordinator =
            LivePresenceAudioCoordinator(
                soundPlayer = sound,
                speechPlayer = speech,
                cueDuration = Duration.ZERO,
                itemGap = Duration.ZERO,
            )
        val joined =
            LivePresenceAnnouncement(
                roleLabel = "管理员",
                roomDisplayName = "小林",
                action = LivePresenceAnnouncementAction.joined,
            )
        val removed =
            LivePresenceAnnouncement(
                roleLabel = "成员",
                roomDisplayName = "小周",
                action = LivePresenceAnnouncementAction.removed,
            )

        waitAll(
            listOf(
                coordinator.play(LivePresenceSound.joined, volume = 0.7, announcement = joined),
                coordinator.play(LivePresenceSound.left, volume = 0.7, announcement = removed),
            ),
        )

        assertEquals(
            listOf(
                "sound:joined",
                "speech:管理员|小林|进入了语音频道",
                "sound:left",
                "speech:成员|小周|被踢出了语音频道",
            ),
            events,
        )
    }

    @Test
    fun `presence audio without announcement only plays the cue`() = gang.runTest {
        val events = mutableListOf<String>()
        val coordinator =
            LivePresenceAudioCoordinator(
                soundPlayer = RecordingSoundPlayer(events),
                speechPlayer = RecordingSpeechPlayer(events),
                cueDuration = Duration.ZERO,
                itemGap = Duration.ZERO,
            )

        coordinator.play(LivePresenceSound.joined, volume = 0.7).await()

        assertEquals(listOf("sound:joined"), events)
    }
}

private class RecordingSoundPlayer(val events: MutableList<String>) : LivePresenceSoundPlayer {
    override suspend fun play(sound: LivePresenceSound, volume: Double) {
        events.add("sound:${sound.name}")
    }

    override suspend fun dispose() {}
}

private class RecordingSpeechPlayer(val events: MutableList<String>) : LivePresenceSpeechPlayer {
    override suspend fun speak(announcement: LivePresenceAnnouncement, volume: Double) {
        events.add("speech:${announcement.segments.joinToString("|")}")
    }

    override suspend fun dispose() {}
}
