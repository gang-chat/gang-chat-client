package com.gangchat.client.shell

import com.gangchat.client.foundation.StreamSubscription
import com.gangchat.client.foundation.ValueNotifier
import com.gangchat.client.platform.AudioPlayer
import com.gangchat.client.platform.PlayerState
import com.gangchat.client.platform.UrlSource
import kotlin.time.Duration

class VoicePlaybackSnapshot(
    val activeMessageId: String? = null,
    val playing: Boolean = false,
    val position: Duration = Duration.ZERO,
    val duration: Duration = Duration.ZERO,
) {
    fun isPlaying(messageId: String): Boolean {
        return playing && activeMessageId == messageId
    }
}

class VoicePlaybackService(player: AudioPlayer? = null) {
    private val _player: AudioPlayer = player ?: AudioPlayer()
    val state: ValueNotifier<VoicePlaybackSnapshot> = ValueNotifier(VoicePlaybackSnapshot())
    private var _stateSubscription: StreamSubscription<PlayerState>? = null
    private var _positionSubscription: StreamSubscription<Duration>? = null
    private var _durationSubscription: StreamSubscription<Duration>? = null
    private var _completeSubscription: StreamSubscription<Unit>? = null

    init {
        _stateSubscription = _player.onPlayerStateChanged.listen(::_handleState)
        _positionSubscription = _player.onPositionChanged.listen(::_handlePosition)
        _durationSubscription = _player.onDurationChanged.listen(::_handleDuration)
        _completeSubscription = _player.onPlayerComplete.listen({ state.value = VoicePlaybackSnapshot() })
    }

    suspend fun toggle(messageId: String, resolvedUrl: String) {
        if (state.value.isPlaying(messageId)) {
            stop()
            return
        }
        play(messageId = messageId, resolvedUrl = resolvedUrl)
    }

    suspend fun play(messageId: String, resolvedUrl: String) {
        _player.stop()
        state.value =
            VoicePlaybackSnapshot(
                activeMessageId = messageId,
                playing = true,
                position = Duration.ZERO,
                duration = Duration.ZERO,
            )
        try {
            _player.play(UrlSource(resolvedUrl))
        } catch (error: Exception) {
            if (state.value.activeMessageId == messageId) {
                state.value = VoicePlaybackSnapshot()
            }
            throw error
        }
    }

    suspend fun stop() {
        _player.stop()
        state.value = VoicePlaybackSnapshot()
    }

    suspend fun dispose() {
        _stateSubscription?.cancel()
        _positionSubscription?.cancel()
        _durationSubscription?.cancel()
        _completeSubscription?.cancel()
        state.dispose()
        _player.dispose()
    }

    private fun _handlePosition(position: Duration) {
        val current = state.value
        if (current.activeMessageId == null) return
        state.value =
            VoicePlaybackSnapshot(
                activeMessageId = current.activeMessageId,
                playing = current.playing,
                position = position,
                duration = current.duration,
            )
    }

    private fun _handleDuration(duration: Duration) {
        val current = state.value
        if (current.activeMessageId == null) return
        state.value =
            VoicePlaybackSnapshot(
                activeMessageId = current.activeMessageId,
                playing = current.playing,
                position = current.position,
                duration = duration,
            )
    }

    private fun _handleState(playerState: PlayerState) {
        if (playerState == PlayerState.completed ||
            playerState == PlayerState.stopped ||
            playerState == PlayerState.disposed
        ) {
            state.value = VoicePlaybackSnapshot()
        }
    }
}
