package com.gangchat.client.app

import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.GangApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * The per-session service graph of the authenticated shell: one API client
 * and the controllers built on it. Created by the home shell for the signed-in
 * account and closed when the account changes or the shell goes away.
 */
class AuthenticatedAppServices private constructor(
    val context: AuthenticatedAppContext,
    val api: GangApi,
    val rooms: RoomsController,
    val messages: MessagesController,
    val roomReads: RoomReadSyncController,
    val live: LiveController,
    val liveSession: LiveSessionController,
    val realtime: RealtimeService,
    val musicBox: MusicBoxController,
    val fileDownloads: FileDownloadsController,
    val mediaCache: MediaCacheController,
    val search: GlobalSearchController,
    val settings: SettingsController,
    val stickers: StickerPacksController,
    val voiceRecorder: VoiceRecorderController,
    val ownsLiveSession: Boolean,
    val ownsRealtime: Boolean,
) {
    suspend fun warmPersonalStickerCache(userId: String) {
        try {
            stickers.loadPersonalPacks(userId = userId)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Cache warmup is opportunistic; callers should keep rendering the last
            // available state even if the network is down.
        }
    }

    fun close() {
        roomReads.close()
        if (ownsRealtime) realtime.dispose()
        if (ownsLiveSession) liveSession.dispose()
        unawaited { voiceRecorder.dispose() }
        api.close()
    }

    companion object {
        /** Dart's unnamed `factory AuthenticatedAppServices(context, ...)`. */
        operator fun invoke(
            context: AuthenticatedAppContext,
            audioDeviceStore: AudioDeviceStore,
            liveSessionController: LiveSessionController? = null,
            realtime: RealtimeService? = null,
        ): AuthenticatedAppServices {
            val api = context.createApiClient()
            val liveSession =
                liveSessionController
                    ?: LiveSessionController(
                        apiBaseUrl = context.apiBaseUrl,
                        audioDeviceStore = audioDeviceStore,
                        screenAudioTokenProvider = { roomId -> api.issueScreenAudioToken(roomId = roomId) },
                    )
            val messages = MessagesController(api = api)
            return AuthenticatedAppServices(
                context = context,
                api = api,
                rooms = RoomsController(api = api),
                messages = messages,
                roomReads = RoomReadSyncController(messages = messages),
                live = LiveController(api = api),
                liveSession = liveSession,
                realtime =
                    realtime
                        ?: RealtimeController(
                            apiBaseUrl = context.apiBaseUrl,
                            accessTokenProvider = context.accessTokenProvider,
                        ),
                musicBox = MusicBoxController(api = api),
                fileDownloads = FileDownloadsController(),
                mediaCache = MediaCacheController(),
                search = GlobalSearchController(api = api),
                settings =
                    SettingsController(
                        api = api,
                        apiBaseUrl = context.apiBaseUrl,
                        stickerPackStore = context.stickerPackStore,
                    ),
                stickers =
                    StickerPacksController(
                        api = api,
                        apiBaseUrl = context.apiBaseUrl,
                        stickerPackStore = context.stickerPackStore,
                    ),
                voiceRecorder = VoiceRecorderController(),
                ownsLiveSession = liveSessionController == null,
                ownsRealtime = realtime == null,
            )
        }
    }
}
