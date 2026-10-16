import { unawaited } from '../lib/async';
import type { GangApi } from '../protocol/api_client';
import type { AudioDeviceStore } from './audio_device_store';
import type { AuthenticatedAppContext } from './authenticated_app_context';
import { FileDownloadsController } from './file_downloads_controller';
import { GlobalSearchController } from './global_search_controller';
import { LiveController } from './live_controller';
import { LiveSessionController } from './live_session_controller';
import { MediaCacheController } from './media_cache_controller';
import { MessagesController } from './messages_controller';
import { MusicBoxController } from './music_box_controller';
import { RealtimeController, type RealtimeService } from './realtime_controller';
import { RoomReadSyncController } from './room_read_sync_controller';
import { RoomsController } from './rooms_controller';
import { SettingsController } from './settings_controller';
import { StickerPacksController } from './sticker_packs_controller';
import { VoiceRecorderController } from './voice_recorder_controller';

export class AuthenticatedAppServices {
  /**
   * Port of the Dart factory constructor: builds every service for an
   * authenticated [context]. Injected [liveSessionController] / [realtime]
   * instances are borrowed and not disposed by [close].
   */
  constructor(
    context: AuthenticatedAppContext,
    opts: {
      audioDeviceStore: AudioDeviceStore;
      liveSessionController?: LiveSessionController | null;
      realtime?: RealtimeService | null;
    },
  ) {
    const liveSessionController = opts.liveSessionController ?? null;
    const realtime = opts.realtime ?? null;
    const api = context.createApiClient();
    const liveSession =
      liveSessionController ??
      new LiveSessionController({
        apiBaseUrl: context.apiBaseUrl,
        audioDeviceStore: opts.audioDeviceStore,
        screenAudioTokenProvider: (roomId) => api.issueScreenAudioToken({ roomId }),
      });
    const messages = new MessagesController({ api });
    this.context = context;
    this.api = api;
    this.rooms = new RoomsController({ api });
    this.messages = messages;
    this.roomReads = new RoomReadSyncController({ messages });
    this.live = new LiveController({ api });
    this.liveSession = liveSession;
    this.realtime =
      realtime ??
      new RealtimeController({
        apiBaseUrl: context.apiBaseUrl,
        accessTokenProvider: context.accessTokenProvider,
      });
    this.musicBox = new MusicBoxController({ api });
    this.fileDownloads = new FileDownloadsController();
    this.mediaCache = new MediaCacheController();
    this.search = new GlobalSearchController({ api });
    this.settings = new SettingsController({
      api,
      apiBaseUrl: context.apiBaseUrl,
      stickerPackStore: context.stickerPackStore,
    });
    this.stickers = new StickerPacksController({
      api,
      apiBaseUrl: context.apiBaseUrl,
      stickerPackStore: context.stickerPackStore,
    });
    this.voiceRecorder = new VoiceRecorderController();
    this.ownsLiveSession = liveSessionController == null;
    this.ownsRealtime = realtime == null;
  }

  readonly context: AuthenticatedAppContext;
  readonly api: GangApi;
  readonly rooms: RoomsController;
  readonly messages: MessagesController;
  readonly roomReads: RoomReadSyncController;
  readonly live: LiveController;
  readonly liveSession: LiveSessionController;
  readonly realtime: RealtimeService;
  readonly musicBox: MusicBoxController;
  readonly fileDownloads: FileDownloadsController;
  readonly mediaCache: MediaCacheController;
  readonly search: GlobalSearchController;
  readonly settings: SettingsController;
  readonly stickers: StickerPacksController;
  readonly voiceRecorder: VoiceRecorderController;
  readonly ownsLiveSession: boolean;
  readonly ownsRealtime: boolean;

  async warmPersonalStickerCache(opts: { userId: string }): Promise<void> {
    try {
      await this.stickers.loadPersonalPacks({ userId: opts.userId });
    } catch {
      // Cache warmup is opportunistic; callers should keep rendering the last
      // available state even if the network is down.
    }
  }

  close(): void {
    this.roomReads.close();
    if (this.ownsRealtime) this.realtime.dispose();
    if (this.ownsLiveSession) this.liveSession.dispose();
    unawaited(this.voiceRecorder.dispose());
    this.api.close();
  }
}
