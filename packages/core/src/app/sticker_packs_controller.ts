import type { GangApi } from '../protocol/api_client';
import type { StickerPack } from '../protocol/models';
import type { StickerPackStore } from '../protocol/sticker_pack_store';

export class StickerPacksController {
  constructor(opts: { api: GangApi; apiBaseUrl: string; stickerPackStore: StickerPackStore }) {
    this.api = opts.api;
    this.apiBaseUrl = opts.apiBaseUrl;
    this.stickerPackStore = opts.stickerPackStore;
  }

  readonly api: GangApi;
  readonly apiBaseUrl: string;
  readonly stickerPackStore: StickerPackStore;

  readCachedPersonalPacks(opts: { userId: string }): Promise<StickerPack[] | null> {
    return this.stickerPackStore.readPersonalPacks({
      userId: opts.userId,
      apiBaseUrl: this.apiBaseUrl,
    });
  }

  async loadPersonalPacks(opts: { userId: string; forceReload?: boolean }): Promise<StickerPack[]> {
    const { userId, forceReload = false } = opts;
    if (!forceReload) {
      const cached = await this.readCachedPersonalPacks({ userId });
      if (cached != null) return cached;
    }

    const packs = await this.api.listStickerPacks({ scope: 'personal' });
    await this.stickerPackStore.writePersonalPacks({
      userId,
      apiBaseUrl: this.apiBaseUrl,
      packs,
    });
    return packs;
  }

  loadRoomPacks(roomId: string): Promise<StickerPack[]> {
    return this.api.listStickerPacks({ scope: 'room', roomId });
  }

  async saveSticker(opts: {
    roomId: string;
    stickerId: string;
    targetScope: string;
    userId: string;
    sourceMessageId?: string | null;
    name?: string | null;
  }): Promise<StickerPack> {
    const { roomId, stickerId, targetScope, userId, sourceMessageId, name } = opts;
    const pack = await this.api.saveSticker({
      roomId,
      stickerId,
      sourceMessageId: sourceMessageId ?? undefined,
      targetScope,
      name: name ?? undefined,
    });
    if (targetScope === 'personal') {
      const packs = await this.api.listStickerPacks({ scope: 'personal' });
      await this.stickerPackStore.writePersonalPacks({
        userId,
        apiBaseUrl: this.apiBaseUrl,
        packs,
      });
    }
    return pack;
  }
}
