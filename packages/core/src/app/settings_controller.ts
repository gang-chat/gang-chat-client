import type { DownloadedFile, GangApi } from '../protocol/api_client';
import type {
  AppVersionInfo,
  CurrentUser,
  Sticker,
  StickerPack,
  UploadedAsset,
  UserSession,
  UserSummary,
} from '../protocol/models';
import type { StickerPackStore } from '../protocol/sticker_pack_store';

export class PersonalStickerPacksResult {
  constructor(opts: { packs: StickerPack[]; fromCache: boolean }) {
    this.packs = opts.packs;
    this.fromCache = opts.fromCache;
  }

  readonly packs: StickerPack[];
  readonly fromCache: boolean;
}

export class SettingsController {
  constructor(opts: {
    api: GangApi | null;
    apiBaseUrl: string;
    stickerPackStore: StickerPackStore;
    managedUserId?: string | null;
  }) {
    this.api = opts.api;
    this.apiBaseUrl = opts.apiBaseUrl;
    this.stickerPackStore = opts.stickerPackStore;
    this.managedUserId = opts.managedUserId ?? null;
  }

  readonly api: GangApi | null;
  readonly apiBaseUrl: string;
  readonly stickerPackStore: StickerPackStore;
  readonly managedUserId: string | null;

  get hasApi(): boolean {
    return this.api != null;
  }

  get isManagingUser(): boolean {
    return this.managedUserId != null;
  }

  get canDeleteAccount(): boolean {
    return true;
  }

  checkAppVersion(): Promise<AppVersionInfo | null> {
    const client = this.api;
    if (client == null) return Promise.resolve(null);
    return client.getAppVersion();
  }

  loadAccount(): Promise<CurrentUser | null> {
    const client = this.api;
    if (client == null) return Promise.resolve(null);
    const targetUserId = this.managedUserId;
    if (targetUserId != null) {
      return client.getForcedUserSettings(targetUserId);
    }
    return client.me();
  }

  searchUsers(opts: { query: string; limit?: number }): Promise<UserSummary[] | null> {
    const { query, limit = 20 } = opts;
    const client = this.api;
    if (client == null) return Promise.resolve(null);
    return client.searchUsers({ query, limit });
  }

  loadSessions(): Promise<UserSession[] | null> {
    const client = this.api;
    if (client == null) return Promise.resolve(null);
    const targetUserId = this.managedUserId;
    if (targetUserId != null) {
      return client.listForcedUserSessions(targetUserId);
    }
    return client.listSessions();
  }

  async loadPersonalStickerPacks(opts: {
    userId: string;
    forceReload?: boolean;
  }): Promise<PersonalStickerPacksResult | null> {
    const { userId, forceReload = false } = opts;
    if (!forceReload) {
      const cached = await this.stickerPackStore.readPersonalPacks({ userId, apiBaseUrl: this.apiBaseUrl });
      if (cached != null) {
        return new PersonalStickerPacksResult({ packs: cached, fromCache: true });
      }
    }

    const client = this.api;
    if (client == null) return null;
    const packs = await client.listStickerPacks({
      scope: 'personal',
      ownerUserId: this.managedUserId ?? undefined,
    });
    await this.stickerPackStore.writePersonalPacks({ userId, apiBaseUrl: this.apiBaseUrl, packs });
    return new PersonalStickerPacksResult({ packs, fromCache: false });
  }

  updateAccount(
    opts: {
      username?: string | null;
      email?: string | null;
      emailVerificationToken?: string | null;
      emailPublic?: boolean | null;
      phoneNumber?: string | null;
      phoneNumberPublic?: boolean | null;
      language?: string | null;
    } = {},
  ): Promise<CurrentUser | null> {
    const client = this.api;
    if (client == null) return Promise.resolve(null);
    const targetUserId = this.managedUserId;
    if (targetUserId != null) {
      return client.updateForcedUserSettings({
        userId: targetUserId,
        username: opts.username ?? undefined,
        email: opts.email ?? undefined,
        emailVerified: opts.emailVerificationToken == null ? undefined : true,
        emailPublic: opts.emailPublic ?? undefined,
        phoneNumber: opts.phoneNumber ?? undefined,
        phoneNumberPublic: opts.phoneNumberPublic ?? undefined,
        language: opts.language ?? undefined,
      });
    }
    return client.updateAccount({
      username: opts.username ?? undefined,
      email: opts.email ?? undefined,
      emailVerificationToken: opts.emailVerificationToken ?? undefined,
      emailPublic: opts.emailPublic ?? undefined,
      phoneNumber: opts.phoneNumber ?? undefined,
      phoneNumberPublic: opts.phoneNumberPublic ?? undefined,
      language: opts.language ?? undefined,
    });
  }

  updateProfile(
    opts: {
      displayName?: string | null;
      bio?: string | null;
      gender?: string | null;
      avatarAssetId?: string | null;
      defaultAvatarKey?: string | null;
    } = {},
  ): Promise<CurrentUser | null> {
    const client = this.api;
    if (client == null) return Promise.resolve(null);
    const targetUserId = this.managedUserId;
    if (targetUserId != null) {
      return client.updateForcedUserSettings({
        userId: targetUserId,
        displayName: opts.displayName ?? undefined,
        bio: opts.bio ?? undefined,
        gender: opts.gender ?? undefined,
        avatarAssetId: opts.avatarAssetId ?? undefined,
        defaultAvatarKey: opts.defaultAvatarKey ?? undefined,
      });
    }
    return client.updateProfile({
      displayName: opts.displayName ?? undefined,
      bio: opts.bio ?? undefined,
      gender: opts.gender ?? undefined,
      avatarAssetId: opts.avatarAssetId ?? undefined,
      defaultAvatarKey: opts.defaultAvatarKey ?? undefined,
    });
  }

  uploadImageAsset(opts: { bytes: Uint8Array; filename: string; purpose: string }): Promise<UploadedAsset | null> {
    const client = this.api;
    if (client == null) return Promise.resolve(null);
    return client.uploadImageAsset({ bytes: opts.bytes, filename: opts.filename, purpose: opts.purpose });
  }

  createStickerPack(opts: { name: string; sortOrder?: number | null }): Promise<StickerPack | null> {
    const client = this.api;
    if (client == null) return Promise.resolve(null);
    return client.createStickerPack({
      name: opts.name,
      sortOrder: opts.sortOrder ?? undefined,
      ownerUserId: this.managedUserId ?? undefined,
    });
  }

  addSticker(opts: { packId: string; assetId: string; name: string; sortOrder?: number | null }): Promise<void> {
    const client = this.api;
    if (client == null) return Promise.resolve();
    return client.addSticker({
      packId: opts.packId,
      assetId: opts.assetId,
      name: opts.name,
      sortOrder: opts.sortOrder ?? undefined,
      ownerUserId: this.managedUserId ?? undefined,
    });
  }

  deleteSticker(opts: { packId: string; stickerId: string }): Promise<void> {
    const client = this.api;
    if (client == null) return Promise.resolve();
    return client.deleteSticker({
      packId: opts.packId,
      stickerId: opts.stickerId,
      ownerUserId: this.managedUserId ?? undefined,
    });
  }

  updateSticker(opts: {
    packId: string;
    stickerId: string;
    name?: string | null;
    sortOrder?: number | null;
  }): Promise<Sticker | null> {
    const client = this.api;
    if (client == null) return Promise.resolve(null);
    return client.updateSticker({
      packId: opts.packId,
      stickerId: opts.stickerId,
      name: opts.name ?? undefined,
      sortOrder: opts.sortOrder ?? undefined,
    });
  }

  async reorderStickers(opts: { packId: string; stickerIds: string[] }): Promise<void> {
    const client = this.api;
    if (client == null) return;
    await client.reorderStickers({ packId: opts.packId, stickerIds: opts.stickerIds });
  }

  downloadStickers(opts: { stickerIds: string[] }): Promise<DownloadedFile | null> {
    const client = this.api;
    if (client == null) return Promise.resolve(null);
    return client.downloadStickers({ stickerIds: opts.stickerIds });
  }

  changePassword(opts: { currentPassword: string; newPassword: string }): Promise<void> {
    const client = this.api;
    if (client == null) return Promise.resolve();
    const targetUserId = this.managedUserId;
    if (targetUserId != null) {
      return client.forceResetUserPassword({ userId: targetUserId, newPassword: opts.newPassword });
    }
    return client.changePassword({ currentPassword: opts.currentPassword, newPassword: opts.newPassword });
  }

  deleteMyAccount(): Promise<void> {
    const client = this.api;
    if (client == null) return Promise.resolve();
    const targetUserId = this.managedUserId;
    if (targetUserId != null) {
      return client.forceDeleteUserAccount(targetUserId);
    }
    return client.deleteMyAccount({ confirm: true });
  }

  setManagedAccountSuspended(suspended: boolean): Promise<CurrentUser | null> {
    const client = this.api;
    const targetUserId = this.managedUserId;
    if (client == null || targetUserId == null) return Promise.resolve(null);
    return client.updateForcedUserSettings({
      userId: targetUserId,
      status: suspended ? 'suspended' : 'active',
    });
  }
}
