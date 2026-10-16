import type {
  PersonalMusicPlaylistApi,
  PersonalMusicPlaylistBatchAddApi,
  PersonalMusicPlaylistMergeApi,
  RoomMusicPlaylistApi,
  RoomMusicPlaylistBatchAddApi,
  RoomMusicPlaylistCloneApi,
  RoomMusicPlaylistImportApi,
  RoomMusicPlaylistMergeApi,
} from '../protocol/api_client';
import type {
  MusicBoxSearchResult,
  PersonalMusicPlaylist,
  PersonalMusicPlaylistBatchAddResult,
  PersonalMusicPlaylistItem,
  PersonalMusicPlaylistItemsPage,
  PersonalMusicPlaylistMergeResult,
  PersonalMusicPlaylistPage,
} from '../protocol/models';

export const personalMusicPlaylistPageSize = 50;
export const personalPlaylistCountAll = '';
export const personalPlaylistCountEmpty = 'empty';
export const personalPlaylistCount1To10 = '1-10';
export const personalPlaylistCount11To50 = '11-50';
export const personalPlaylistCount51To100 = '51-100';
export const personalPlaylistCountOver100 = '101+';

export type MusicPlaylistTrackSearch = (opts: { keyword: string; source: string }) => Promise<MusicBoxSearchResult[]>;

export class PersonalPlaylistFilterDraft {
  constructor(opts: { keyword: string; countFilter: string }) {
    this.keyword = opts.keyword;
    this.countFilter = opts.countFilter;
  }

  readonly keyword: string;
  readonly countFilter: string;
}

export class PersonalPlaylistCreateDraft {
  constructor(opts: { name: string; importPlaylistId?: string | null }) {
    this.name = opts.name;
    this.importPlaylistId = opts.importPlaylistId ?? null;
  }

  readonly name: string;
  readonly importPlaylistId: string | null;
}

export class PersonalPlaylistItemFilterDraft {
  constructor(opts: { keyword: string; source: string }) {
    this.keyword = opts.keyword;
    this.source = opts.source;
  }

  readonly keyword: string;
  readonly source: string;
}

export class MusicPlaylistManagementCapabilities {
  constructor(
    opts: {
      canCreatePlaylists?: boolean;
      canRenamePlaylists?: boolean;
      canDeletePlaylists?: boolean;
      canReorderPlaylists?: boolean;
      canAddItems?: boolean;
      canDeleteItems?: boolean;
      canReorderItems?: boolean;
    } = {},
  ) {
    this.canCreatePlaylists = opts.canCreatePlaylists ?? true;
    this.canRenamePlaylists = opts.canRenamePlaylists ?? true;
    this.canDeletePlaylists = opts.canDeletePlaylists ?? true;
    this.canReorderPlaylists = opts.canReorderPlaylists ?? true;
    this.canAddItems = opts.canAddItems ?? true;
    this.canDeleteItems = opts.canDeleteItems ?? true;
    this.canReorderItems = opts.canReorderItems ?? true;
  }

  static readOnly(): MusicPlaylistManagementCapabilities {
    return new MusicPlaylistManagementCapabilities({
      canCreatePlaylists: false,
      canRenamePlaylists: false,
      canDeletePlaylists: false,
      canReorderPlaylists: false,
      canAddItems: false,
      canDeleteItems: false,
      canReorderItems: false,
    });
  }

  readonly canCreatePlaylists: boolean;
  readonly canRenamePlaylists: boolean;
  readonly canDeletePlaylists: boolean;
  readonly canReorderPlaylists: boolean;
  readonly canAddItems: boolean;
  readonly canDeleteItems: boolean;
  readonly canReorderItems: boolean;

  get canManagePlaylists(): boolean {
    return this.canRenamePlaylists || this.canDeletePlaylists || this.canReorderPlaylists;
  }

  get canManageItems(): boolean {
    return this.canDeleteItems || this.canReorderItems;
  }
}

interface _MusicPlaylistsBackend {
  readonly identity: object;

  readonly capabilities: MusicPlaylistManagementCapabilities;

  loadPlaylists(): Promise<PersonalMusicPlaylistPage>;

  readonly canImportPersonalPlaylist: boolean;

  loadImportPlaylists(): Promise<PersonalMusicPlaylistPage | null>;

  readonly canCloneRoomPlaylistsToPersonal: boolean;

  cloneRoomPlaylistToPersonal(opts: { playlistId: string }): Promise<PersonalMusicPlaylist>;

  readonly canMergePlaylists: boolean;

  mergePlaylists(opts: { name: string; playlistIds: string[] }): Promise<PersonalMusicPlaylistMergeResult>;

  readonly canBatchAddItems: boolean;

  batchAddItems(opts: {
    sourcePlaylistId: string;
    targetPlaylistId: string;
    itemIds: string[];
  }): Promise<PersonalMusicPlaylistBatchAddResult>;

  createPlaylist(opts: { name: string; importPlaylistId?: string | null }): Promise<PersonalMusicPlaylist>;

  renamePlaylist(opts: { playlistId: string; name: string }): Promise<PersonalMusicPlaylist>;

  deletePlaylist(playlistId: string): Promise<void>;

  pinPlaylists(opts: { playlistIds: string[] }): Promise<void>;

  movePlaylist(opts: { playlistId: string; direction: string }): Promise<void>;

  loadItems(opts: {
    playlistId: string;
    page: number;
    pageSize: number;
    keyword?: string | null;
    source?: string | null;
  }): Promise<PersonalMusicPlaylistItemsPage>;

  searchTracks(opts: { keyword: string; source: string }): Promise<MusicBoxSearchResult[]>;

  addTrack(opts: { playlistId: string; track: MusicBoxSearchResult }): Promise<PersonalMusicPlaylistItem>;

  deleteItems(opts: { playlistId: string; itemIds: string[] }): Promise<void>;

  moveItem(opts: { playlistId: string; itemId: string; direction: string }): Promise<void>;

  reorderItems(opts: { playlistId: string; itemIds: string[] }): Promise<void>;
}

export class PersonalMusicPlaylistsController {
  constructor(api: PersonalMusicPlaylistApi | null, opts: { onChanged?: (() => void) | null } = {}) {
    this._backend = api == null ? null : new _PersonalMusicPlaylistsBackend(api);
    this._onChanged = opts.onChanged ?? null;
  }

  /** Port of the `PersonalMusicPlaylistsController.room(...)` named constructor. */
  static room(opts: {
    roomApi: RoomMusicPlaylistApi | null;
    personalApi?: PersonalMusicPlaylistApi | null;
    roomId: string;
    canManage: boolean;
    searchTracks: MusicPlaylistTrackSearch;
    onChanged?: (() => void) | null;
  }): PersonalMusicPlaylistsController {
    const controller = new PersonalMusicPlaylistsController(null, { onChanged: opts.onChanged });
    controller._backend =
      opts.roomApi == null
        ? null
        : new _RoomMusicPlaylistsBackend({
            roomApi: opts.roomApi,
            personalApi: opts.personalApi ?? null,
            roomId: opts.roomId,
            canManage: opts.canManage,
            searchTracksCallback: opts.searchTracks,
          });
    return controller;
  }

  // Assigned once, by the constructor or by [room].
  private _backend: _MusicPlaylistsBackend | null;
  private readonly _onChanged: (() => void) | null;

  private _notifyChanged(): void {
    this._onChanged?.();
  }

  /**
   * Identity of the backing API, compared with `!==` by widgets to detect a
   * changed data source. Room-scoped controllers return an interned tuple so
   * equal `(roomApi, personalApi, roomId, canManage)` stay `===`, like the Dart
   * record they port.
   */
  get api(): object | null {
    return this._backend?.identity ?? null;
  }

  get capabilities(): MusicPlaylistManagementCapabilities {
    return this._backend?.capabilities ?? MusicPlaylistManagementCapabilities.readOnly();
  }

  get available(): boolean {
    return this._backend != null;
  }

  get roomScoped(): boolean {
    return this._backend instanceof _RoomMusicPlaylistsBackend;
  }

  get sourceRoomId(): string | null {
    const backend = this._backend;
    return backend instanceof _RoomMusicPlaylistsBackend ? backend.roomId : null;
  }

  get canImportPersonalPlaylist(): boolean {
    return this._backend?.canImportPersonalPlaylist ?? false;
  }

  get canCloneRoomPlaylistsToPersonal(): boolean {
    return this._backend?.canCloneRoomPlaylistsToPersonal ?? false;
  }

  get canMergePlaylists(): boolean {
    return this._backend?.canMergePlaylists ?? false;
  }

  get canBatchAddItems(): boolean {
    return this._backend?.canBatchAddItems ?? false;
  }

  loadPlaylists(): Promise<PersonalMusicPlaylistPage | null> {
    const client = this._backend;
    if (client == null) return Promise.resolve(null);
    return client.loadPlaylists();
  }

  loadImportPlaylists(): Promise<PersonalMusicPlaylistPage | null> {
    return this._backend?.loadImportPlaylists() ?? Promise.resolve(null);
  }

  /**
   * Loads the current account's writable playlists even while this
   * controller is managing a room-scoped playlist collection.
   */
  loadPersonalPlaylistTargets(): Promise<PersonalMusicPlaylistPage | null> {
    const client = this._backend;
    if (client instanceof _PersonalMusicPlaylistsBackend) {
      return client.api.listPersonalMusicPlaylists({
        page: 1,
        pageSize: personalMusicPlaylistPageSize,
      });
    }
    if (client instanceof _RoomMusicPlaylistsBackend) {
      const api = client.personalApi;
      if (api == null) return Promise.resolve(null);
      return api.listPersonalMusicPlaylists({
        page: 1,
        pageSize: personalMusicPlaylistPageSize,
      });
    }
    return Promise.resolve(null);
  }

  async cloneRoomPlaylistToPersonal(playlistId: string): Promise<PersonalMusicPlaylist | null> {
    const normalized = playlistId.trim();
    const client = this._backend;
    if (normalized.length === 0 || client == null) return null;
    const result = await client.cloneRoomPlaylistToPersonal({ playlistId: normalized });
    this._notifyChanged();
    return result;
  }

  async mergePlaylists(opts: { name: string; playlistIds: string[] }): Promise<PersonalMusicPlaylistMergeResult | null> {
    const { name, playlistIds } = opts;
    const normalizedName = normalizedPersonalPlaylistName(name);
    const normalizedIDs = uniquePersonalPlaylistIds(playlistIds);
    if (normalizedName == null || normalizedIDs.length < 2 || normalizedIDs.length !== playlistIds.length) {
      return null;
    }
    const client = this._backend;
    if (client == null) return null;
    const result = await client.mergePlaylists({ name: normalizedName, playlistIds: normalizedIDs });
    this._notifyChanged();
    return result;
  }

  async batchAddItems(opts: {
    sourcePlaylistId: string;
    targetPlaylistId: string;
    itemIds: Iterable<string>;
  }): Promise<PersonalMusicPlaylistBatchAddResult | null> {
    const sourceID = opts.sourcePlaylistId.trim();
    const targetID = opts.targetPlaylistId.trim();
    const requestedItemIDs = [...opts.itemIds];
    const normalizedItemIDs = uniquePersonalPlaylistItemIds(requestedItemIDs);
    if (
      sourceID.length === 0 ||
      targetID.length === 0 ||
      sourceID === targetID ||
      normalizedItemIDs.length === 0 ||
      normalizedItemIDs.length !== requestedItemIDs.length ||
      normalizedItemIDs.length > 500
    ) {
      return null;
    }
    const client = this._backend;
    if (client == null) return null;
    const result = await client.batchAddItems({
      sourcePlaylistId: sourceID,
      targetPlaylistId: targetID,
      itemIds: normalizedItemIDs,
    });
    this._notifyChanged();
    return result;
  }

  async createPlaylist(name: string, opts: { importPlaylistId?: string | null } = {}): Promise<PersonalMusicPlaylist | null> {
    const normalized = normalizedPersonalPlaylistName(name);
    if (normalized == null) return null;
    const client = this._backend;
    if (client == null) return null;
    const result = await client.createPlaylist({
      name: normalized,
      importPlaylistId: opts.importPlaylistId?.trim(),
    });
    this._notifyChanged();
    return result;
  }

  async renamePlaylist(opts: { playlistId: string; name: string }): Promise<PersonalMusicPlaylist | null> {
    const normalized = normalizedPersonalPlaylistName(opts.name);
    if (normalized == null) return null;
    const client = this._backend;
    if (client == null) return null;
    const result = await client.renamePlaylist({ playlistId: opts.playlistId, name: normalized });
    this._notifyChanged();
    return result;
  }

  async deletePlaylist(playlistId: string): Promise<void> {
    const client = this._backend;
    if (client == null) return;
    await client.deletePlaylist(playlistId);
    this._notifyChanged();
  }

  async pinPlaylists(playlistIds: readonly string[]): Promise<void> {
    const ids = uniquePersonalPlaylistIds(playlistIds);
    const client = this._backend;
    if (ids.length === 0 || client == null) return;
    await client.pinPlaylists({ playlistIds: ids });
    this._notifyChanged();
  }

  async movePlaylist(opts: { playlistId: string; delta: number }): Promise<void> {
    const { playlistId, delta } = opts;
    if (delta !== -1 && delta !== 1) {
      throw new Error(`Invalid argument (delta): must be -1 or 1: ${delta}`);
    }
    const client = this._backend;
    if (client == null) return;
    await client.movePlaylist({ playlistId, direction: delta < 0 ? 'up' : 'down' });
    this._notifyChanged();
  }

  loadItems(opts: {
    playlistId: string;
    page?: number;
    keyword?: string;
    source?: string;
  }): Promise<PersonalMusicPlaylistItemsPage | null> {
    const { playlistId, page = 1, keyword = '', source = '' } = opts;
    const client = this._backend;
    if (client == null) return Promise.resolve(null);
    return client.loadItems({
      playlistId,
      page,
      pageSize: personalMusicPlaylistPageSize,
      keyword,
      source,
    });
  }

  searchTracks(opts: { keyword: string; source: string }): Promise<MusicBoxSearchResult[] | null> {
    const normalizedKeyword = opts.keyword.trim();
    if (normalizedKeyword.length === 0) {
      return Promise.resolve([]);
    }
    const client = this._backend;
    if (client == null) return Promise.resolve(null);
    return client.searchTracks({ keyword: normalizedKeyword, source: opts.source });
  }

  async addTrack(opts: { playlistId: string; track: MusicBoxSearchResult }): Promise<PersonalMusicPlaylistItem | null> {
    const client = this._backend;
    if (client == null) return null;
    const result = await client.addTrack({ playlistId: opts.playlistId, track: opts.track });
    this._notifyChanged();
    return result;
  }

  /**
   * Adds a track to the current account's playlist without changing the
   * scope currently managed by this controller.
   */
  async addTrackToPersonalPlaylist(opts: {
    playlistId: string;
    track: MusicBoxSearchResult;
  }): Promise<PersonalMusicPlaylistItem | null> {
    const client = this._backend;
    const api =
      client instanceof _PersonalMusicPlaylistsBackend
        ? client.api
        : client instanceof _RoomMusicPlaylistsBackend
          ? client.personalApi
          : null;
    if (api == null) return null;
    const result = await api.addPersonalMusicPlaylistItem({
      playlistId: opts.playlistId,
      track: opts.track,
    });
    this._notifyChanged();
    return result;
  }

  async deleteItems(opts: { playlistId: string; itemIds: readonly string[] }): Promise<void> {
    const ids = uniquePersonalPlaylistItemIds(opts.itemIds);
    const client = this._backend;
    if (ids.length === 0 || client == null) return;
    await client.deleteItems({ playlistId: opts.playlistId, itemIds: ids });
    this._notifyChanged();
  }

  async moveItem(opts: { playlistId: string; itemId: string; delta: number }): Promise<void> {
    const { playlistId, itemId, delta } = opts;
    if (delta !== -1 && delta !== 1) {
      throw new Error(`Invalid argument (delta): must be -1 or 1: ${delta}`);
    }
    const client = this._backend;
    if (client == null) return;
    await client.moveItem({ playlistId, itemId, direction: delta < 0 ? 'up' : 'down' });
    this._notifyChanged();
  }

  async pinItems(opts: { playlistId: string; selectedItemIds: Iterable<string> }): Promise<void> {
    const { playlistId } = opts;
    const client = this._backend;
    if (client == null) return;
    const selectedIds = uniquePersonalPlaylistItemIds(opts.selectedItemIds);
    if (selectedIds.length === 0) return;

    const items: PersonalMusicPlaylistItem[] = [];
    let page = 1;
    for (;;) {
      const result = await client.loadItems({
        playlistId,
        page,
        pageSize: personalMusicPlaylistPageSize,
      });
      items.push(...result.items);
      if (!result.hasMore) break;
      if (result.items.length === 0 || items.length >= result.total) {
        throw new Error('歌单分页数据不完整，请刷新后重试');
      }
      page += 1;
    }

    const order = personalPlaylistItemOrderWithSelectionPinnedToFront({
      items,
      selectedItemIds: selectedIds,
    });
    if (order == null) return;
    await client.reorderItems({ playlistId, itemIds: order });
    this._notifyChanged();
  }
}

// Dart checks optional server extensions with `api is XxxApi`. TS interfaces
// have no runtime identity, so the port probes for the extension's method.
function _isPersonalMusicPlaylistMergeApi(api: object): api is PersonalMusicPlaylistMergeApi {
  return 'mergePersonalMusicPlaylists' in api && typeof api.mergePersonalMusicPlaylists === 'function';
}

function _isPersonalMusicPlaylistBatchAddApi(api: object): api is PersonalMusicPlaylistBatchAddApi {
  return 'batchAddPersonalMusicPlaylistItems' in api && typeof api.batchAddPersonalMusicPlaylistItems === 'function';
}

function _isRoomMusicPlaylistImportApi(api: object): api is RoomMusicPlaylistImportApi {
  return (
    'createRoomMusicPlaylistFromPersonal' in api && typeof api.createRoomMusicPlaylistFromPersonal === 'function'
  );
}

function _isRoomMusicPlaylistCloneApi(api: object): api is RoomMusicPlaylistCloneApi {
  return 'cloneRoomMusicPlaylistToPersonal' in api && typeof api.cloneRoomMusicPlaylistToPersonal === 'function';
}

function _isRoomMusicPlaylistMergeApi(api: object): api is RoomMusicPlaylistMergeApi {
  return 'mergeRoomMusicPlaylists' in api && typeof api.mergeRoomMusicPlaylists === 'function';
}

function _isRoomMusicPlaylistBatchAddApi(api: object): api is RoomMusicPlaylistBatchAddApi {
  return 'batchAddRoomMusicPlaylistItems' in api && typeof api.batchAddRoomMusicPlaylistItems === 'function';
}

class _PersonalMusicPlaylistsBackend implements _MusicPlaylistsBackend {
  constructor(readonly api: PersonalMusicPlaylistApi) {}

  get identity(): object {
    return this.api;
  }

  get capabilities(): MusicPlaylistManagementCapabilities {
    return new MusicPlaylistManagementCapabilities();
  }

  get canImportPersonalPlaylist(): boolean {
    return false;
  }

  get canCloneRoomPlaylistsToPersonal(): boolean {
    return false;
  }

  get canMergePlaylists(): boolean {
    return _isPersonalMusicPlaylistMergeApi(this.api);
  }

  get canBatchAddItems(): boolean {
    return _isPersonalMusicPlaylistBatchAddApi(this.api);
  }

  cloneRoomPlaylistToPersonal(opts: { playlistId: string }): Promise<PersonalMusicPlaylist> {
    return Promise.reject(new Error('个人歌单不能作为房间歌单的克隆来源'));
  }

  mergePlaylists(opts: { name: string; playlistIds: string[] }): Promise<PersonalMusicPlaylistMergeResult> {
    const mergeApi = this.api;
    if (!_isPersonalMusicPlaylistMergeApi(mergeApi)) {
      return Promise.reject(new Error('当前服务端不支持合并个人歌单'));
    }
    return mergeApi.mergePersonalMusicPlaylists({ name: opts.name, playlistIds: opts.playlistIds });
  }

  batchAddItems(opts: {
    sourcePlaylistId: string;
    targetPlaylistId: string;
    itemIds: string[];
  }): Promise<PersonalMusicPlaylistBatchAddResult> {
    const batchApi = this.api;
    if (!_isPersonalMusicPlaylistBatchAddApi(batchApi)) {
      return Promise.reject(new Error('当前服务端不支持批量添加个人歌单歌曲'));
    }
    return batchApi.batchAddPersonalMusicPlaylistItems({
      sourcePlaylistId: opts.sourcePlaylistId,
      targetPlaylistId: opts.targetPlaylistId,
      itemIds: opts.itemIds,
    });
  }

  loadImportPlaylists(): Promise<PersonalMusicPlaylistPage | null> {
    return Promise.resolve(null);
  }

  loadPlaylists(): Promise<PersonalMusicPlaylistPage> {
    return this.api.listPersonalMusicPlaylists();
  }

  createPlaylist(opts: { name: string; importPlaylistId?: string | null }): Promise<PersonalMusicPlaylist> {
    const { importPlaylistId } = opts;
    if (importPlaylistId != null && importPlaylistId.length > 0) {
      return Promise.reject(new Error('个人歌单不能从另一个个人歌单导入创建'));
    }
    return this.api.createPersonalMusicPlaylist({ name: opts.name });
  }

  renamePlaylist(opts: { playlistId: string; name: string }): Promise<PersonalMusicPlaylist> {
    return this.api.renamePersonalMusicPlaylist({ playlistId: opts.playlistId, name: opts.name });
  }

  deletePlaylist(playlistId: string): Promise<void> {
    return this.api.deletePersonalMusicPlaylist(playlistId);
  }

  pinPlaylists(opts: { playlistIds: string[] }): Promise<void> {
    return this.api.pinPersonalMusicPlaylists({ playlistIds: opts.playlistIds });
  }

  movePlaylist(opts: { playlistId: string; direction: string }): Promise<void> {
    return this.api.movePersonalMusicPlaylist({ playlistId: opts.playlistId, direction: opts.direction });
  }

  loadItems(opts: {
    playlistId: string;
    page: number;
    pageSize: number;
    keyword?: string | null;
    source?: string | null;
  }): Promise<PersonalMusicPlaylistItemsPage> {
    return this.api.getPersonalMusicPlaylist({
      playlistId: opts.playlistId,
      page: opts.page,
      pageSize: opts.pageSize,
      keyword: opts.keyword ?? undefined,
      source: opts.source ?? undefined,
    });
  }

  searchTracks(opts: { keyword: string; source: string }): Promise<MusicBoxSearchResult[]> {
    return this.api.searchPersonalMusicPlaylistTracks({ keyword: opts.keyword, source: opts.source });
  }

  addTrack(opts: { playlistId: string; track: MusicBoxSearchResult }): Promise<PersonalMusicPlaylistItem> {
    return this.api.addPersonalMusicPlaylistItem({ playlistId: opts.playlistId, track: opts.track });
  }

  deleteItems(opts: { playlistId: string; itemIds: string[] }): Promise<void> {
    return this.api.deletePersonalMusicPlaylistItems({ playlistId: opts.playlistId, itemIds: opts.itemIds });
  }

  moveItem(opts: { playlistId: string; itemId: string; direction: string }): Promise<void> {
    return this.api.movePersonalMusicPlaylistItem({
      playlistId: opts.playlistId,
      itemId: opts.itemId,
      direction: opts.direction,
    });
  }

  reorderItems(opts: { playlistId: string; itemIds: string[] }): Promise<void> {
    return this.api.reorderPersonalMusicPlaylistItems({ playlistId: opts.playlistId, itemIds: opts.itemIds });
  }
}

// Interned `(roomApi, personalApi, roomId, canManage)` tuples: the Dart record
// identity compares by value, so equal tuples must stay `===` here.
const _roomBackendIdentities = new WeakMap<object, Map<object | null, Map<string, object>>>();

function _roomBackendIdentity(
  roomApi: RoomMusicPlaylistApi,
  personalApi: PersonalMusicPlaylistApi | null,
  roomId: string,
  canManage: boolean,
): object {
  let byPersonalApi = _roomBackendIdentities.get(roomApi);
  if (byPersonalApi === undefined) {
    byPersonalApi = new Map();
    _roomBackendIdentities.set(roomApi, byPersonalApi);
  }
  let byKey = byPersonalApi.get(personalApi);
  if (byKey === undefined) {
    byKey = new Map();
    byPersonalApi.set(personalApi, byKey);
  }
  const key = `${canManage ? 1 : 0}:${roomId}`;
  let identity = byKey.get(key);
  if (identity === undefined) {
    identity = Object.freeze([roomApi, personalApi, roomId, canManage] as const);
    byKey.set(key, identity);
  }
  return identity;
}

class _RoomMusicPlaylistsBackend implements _MusicPlaylistsBackend {
  constructor(opts: {
    roomApi: RoomMusicPlaylistApi;
    personalApi: PersonalMusicPlaylistApi | null;
    roomId: string;
    canManage: boolean;
    searchTracksCallback: MusicPlaylistTrackSearch;
  }) {
    this.roomApi = opts.roomApi;
    this.personalApi = opts.personalApi;
    this.roomId = opts.roomId;
    this.canManage = opts.canManage;
    this.searchTracksCallback = opts.searchTracksCallback;
  }

  readonly roomApi: RoomMusicPlaylistApi;
  readonly personalApi: PersonalMusicPlaylistApi | null;
  readonly roomId: string;
  readonly canManage: boolean;
  readonly searchTracksCallback: MusicPlaylistTrackSearch;

  private _requireManagePermission(): void {
    if (!this.canManage) {
      throw new Error('当前账号没有管理房间歌单的权限');
    }
  }

  get identity(): object {
    return _roomBackendIdentity(this.roomApi, this.personalApi, this.roomId, this.canManage);
  }

  get capabilities(): MusicPlaylistManagementCapabilities {
    return this.canManage ? new MusicPlaylistManagementCapabilities() : MusicPlaylistManagementCapabilities.readOnly();
  }

  get canImportPersonalPlaylist(): boolean {
    return this.canManage && this.personalApi != null && _isRoomMusicPlaylistImportApi(this.roomApi);
  }

  get canCloneRoomPlaylistsToPersonal(): boolean {
    return this.canManage && _isRoomMusicPlaylistCloneApi(this.roomApi);
  }

  get canMergePlaylists(): boolean {
    return this.canManage && _isRoomMusicPlaylistMergeApi(this.roomApi);
  }

  get canBatchAddItems(): boolean {
    return this.canManage && _isRoomMusicPlaylistBatchAddApi(this.roomApi);
  }

  cloneRoomPlaylistToPersonal(opts: { playlistId: string }): Promise<PersonalMusicPlaylist> {
    this._requireManagePermission();
    const cloneApi = this.roomApi;
    if (!_isRoomMusicPlaylistCloneApi(cloneApi)) {
      return Promise.reject(new Error('当前服务端不支持克隆房间歌单'));
    }
    return cloneApi.cloneRoomMusicPlaylistToPersonal({ roomId: this.roomId, playlistId: opts.playlistId });
  }

  mergePlaylists(opts: { name: string; playlistIds: string[] }): Promise<PersonalMusicPlaylistMergeResult> {
    this._requireManagePermission();
    const mergeApi = this.roomApi;
    if (!_isRoomMusicPlaylistMergeApi(mergeApi)) {
      return Promise.reject(new Error('当前服务端不支持合并房间歌单'));
    }
    return mergeApi.mergeRoomMusicPlaylists({
      roomId: this.roomId,
      name: opts.name,
      playlistIds: opts.playlistIds,
    });
  }

  batchAddItems(opts: {
    sourcePlaylistId: string;
    targetPlaylistId: string;
    itemIds: string[];
  }): Promise<PersonalMusicPlaylistBatchAddResult> {
    this._requireManagePermission();
    const batchApi = this.roomApi;
    if (!_isRoomMusicPlaylistBatchAddApi(batchApi)) {
      return Promise.reject(new Error('当前服务端不支持批量添加房间歌单歌曲'));
    }
    return batchApi.batchAddRoomMusicPlaylistItems({
      roomId: this.roomId,
      sourcePlaylistId: opts.sourcePlaylistId,
      targetPlaylistId: opts.targetPlaylistId,
      itemIds: opts.itemIds,
    });
  }

  loadImportPlaylists(): Promise<PersonalMusicPlaylistPage | null> {
    this._requireManagePermission();
    const api = this.personalApi;
    if (api == null) return Promise.resolve(null);
    return api.listPersonalMusicPlaylists({
      page: 1,
      pageSize: personalMusicPlaylistPageSize,
    });
  }

  loadPlaylists(): Promise<PersonalMusicPlaylistPage> {
    return this.roomApi.listRoomMusicPlaylists({ roomId: this.roomId });
  }

  createPlaylist(opts: { name: string; importPlaylistId?: string | null }): Promise<PersonalMusicPlaylist> {
    this._requireManagePermission();
    const normalizedImportID = opts.importPlaylistId?.trim() ?? '';
    if (normalizedImportID.length > 0) {
      const importApi = this.roomApi;
      if (!_isRoomMusicPlaylistImportApi(importApi)) {
        return Promise.reject(new Error('当前服务端不支持导入我的歌单'));
      }
      return importApi.createRoomMusicPlaylistFromPersonal({
        roomId: this.roomId,
        name: opts.name,
        importPlaylistId: normalizedImportID,
      });
    }
    return this.roomApi.createRoomMusicPlaylist({ roomId: this.roomId, name: opts.name });
  }

  renamePlaylist(opts: { playlistId: string; name: string }): Promise<PersonalMusicPlaylist> {
    this._requireManagePermission();
    return this.roomApi.renameRoomMusicPlaylist({
      roomId: this.roomId,
      playlistId: opts.playlistId,
      name: opts.name,
    });
  }

  deletePlaylist(playlistId: string): Promise<void> {
    this._requireManagePermission();
    return this.roomApi.deleteRoomMusicPlaylist({ roomId: this.roomId, playlistId });
  }

  pinPlaylists(opts: { playlistIds: string[] }): Promise<void> {
    this._requireManagePermission();
    return this.roomApi.pinRoomMusicPlaylists({ roomId: this.roomId, playlistIds: opts.playlistIds });
  }

  movePlaylist(opts: { playlistId: string; direction: string }): Promise<void> {
    this._requireManagePermission();
    return this.roomApi.moveRoomMusicPlaylist({
      roomId: this.roomId,
      playlistId: opts.playlistId,
      direction: opts.direction,
    });
  }

  loadItems(opts: {
    playlistId: string;
    page: number;
    pageSize: number;
    keyword?: string | null;
    source?: string | null;
  }): Promise<PersonalMusicPlaylistItemsPage> {
    return this.roomApi.getRoomMusicPlaylist({
      roomId: this.roomId,
      playlistId: opts.playlistId,
      page: opts.page,
      pageSize: opts.pageSize,
      keyword: opts.keyword ?? undefined,
      source: opts.source ?? undefined,
    });
  }

  searchTracks(opts: { keyword: string; source: string }): Promise<MusicBoxSearchResult[]> {
    return this.searchTracksCallback({ keyword: opts.keyword, source: opts.source });
  }

  addTrack(opts: { playlistId: string; track: MusicBoxSearchResult }): Promise<PersonalMusicPlaylistItem> {
    this._requireManagePermission();
    return this.roomApi.addRoomMusicPlaylistItem({
      roomId: this.roomId,
      playlistId: opts.playlistId,
      track: opts.track,
    });
  }

  deleteItems(opts: { playlistId: string; itemIds: string[] }): Promise<void> {
    this._requireManagePermission();
    return this.roomApi.deleteRoomMusicPlaylistItems({
      roomId: this.roomId,
      playlistId: opts.playlistId,
      itemIds: opts.itemIds,
    });
  }

  moveItem(opts: { playlistId: string; itemId: string; direction: string }): Promise<void> {
    this._requireManagePermission();
    return this.roomApi.moveRoomMusicPlaylistItem({
      roomId: this.roomId,
      playlistId: opts.playlistId,
      itemId: opts.itemId,
      direction: opts.direction,
    });
  }

  reorderItems(opts: { playlistId: string; itemIds: string[] }): Promise<void> {
    this._requireManagePermission();
    return this.roomApi.reorderRoomMusicPlaylistItems({
      roomId: this.roomId,
      playlistId: opts.playlistId,
      itemIds: opts.itemIds,
    });
  }
}

export function normalizedPersonalPlaylistName(value: string): string | null {
  const normalized = value.trim();
  // Count code points, like Dart's `runes.length`.
  if (normalized.length === 0 || Array.from(normalized).length > 64) return null;
  return normalized;
}

export function uniquePersonalPlaylistItemIds(values: Iterable<string>): string[] {
  return _uniqueNonEmptyStrings(values);
}

export function uniquePersonalPlaylistIds(values: Iterable<string>): string[] {
  return _uniqueNonEmptyStrings(values);
}

function _uniqueNonEmptyStrings(values: Iterable<string>): string[] {
  const result: string[] = [];
  const seen = new Set<string>();
  for (const value of values) {
    const normalized = value.trim();
    if (normalized.length === 0 || seen.has(normalized)) continue;
    seen.add(normalized);
    result.push(normalized);
  }
  return result;
}

export function toggledPersonalPlaylistSelection(selected: ReadonlySet<string>, itemId: string): Set<string> {
  const next = new Set(selected);
  if (next.has(itemId)) {
    next.delete(itemId);
  } else {
    next.add(itemId);
  }
  return next;
}

export function personalPlaylistFilterActive(opts: { keyword: string; source: string }): boolean {
  return opts.keyword.trim().length > 0 || opts.source.trim().length > 0;
}

export function personalPlaylistListFilterActive(opts: { keyword: string; countFilter: string }): boolean {
  return opts.keyword.trim().length > 0 || opts.countFilter.trim().length > 0;
}

export function filteredPersonalMusicPlaylists(
  playlists: Iterable<PersonalMusicPlaylist>,
  opts: { keyword: string; countFilter: string },
): PersonalMusicPlaylist[] {
  const needle = opts.keyword.trim().toLowerCase();
  const result: PersonalMusicPlaylist[] = [];
  for (const playlist of playlists) {
    if (
      (needle.length === 0 || playlist.name.toLowerCase().includes(needle)) &&
      personalPlaylistMatchesCountFilter(playlist.itemCount, opts.countFilter)
    ) {
      result.push(playlist);
    }
  }
  return result;
}

export function personalPlaylistMatchesCountFilter(count: number, filter: string): boolean {
  switch (filter) {
    case personalPlaylistCountAll:
      return true;
    case personalPlaylistCountEmpty:
      return count === 0;
    case personalPlaylistCount1To10:
      return count >= 1 && count <= 10;
    case personalPlaylistCount11To50:
      return count >= 11 && count <= 50;
    case personalPlaylistCount51To100:
      return count >= 51 && count <= 100;
    case personalPlaylistCountOver100:
      return count >= 101;
    default:
      return true;
  }
}

export function toggledPersonalPlaylistListSelection(selected: readonly string[], playlistId: string): string[] {
  const next = [...selected];
  const index = next.indexOf(playlistId);
  if (index >= 0) {
    next.splice(index, 1);
  } else {
    next.push(playlistId);
  }
  return next;
}

export function personalPlaylistAllVisibleSelected(opts: {
  selectedPlaylistIds: readonly string[];
  visiblePlaylists: readonly PersonalMusicPlaylist[];
}): boolean {
  const { selectedPlaylistIds, visiblePlaylists } = opts;
  return visiblePlaylists.length > 0 && visiblePlaylists.every((playlist) => selectedPlaylistIds.includes(playlist.id));
}

export function toggledVisiblePersonalPlaylistSelection(opts: {
  selectedPlaylistIds: readonly string[];
  visiblePlaylists: readonly PersonalMusicPlaylist[];
}): string[] {
  const { selectedPlaylistIds, visiblePlaylists } = opts;
  if (visiblePlaylists.length === 0) return [...selectedPlaylistIds];
  const visibleIds = new Set(visiblePlaylists.map((playlist) => playlist.id));
  if (personalPlaylistAllVisibleSelected({ selectedPlaylistIds, visiblePlaylists })) {
    return selectedPlaylistIds.filter((id) => !visibleIds.has(id));
  }
  const next = [...selectedPlaylistIds];
  for (const playlist of visiblePlaylists) {
    if (!next.includes(playlist.id)) next.push(playlist.id);
  }
  return next;
}

export function personalPlaylistSelectionNumbers(selectedPlaylistIds: readonly string[]): Map<string, number> {
  const numbers = new Map<string, number>();
  selectedPlaylistIds.forEach((id, index) => numbers.set(id, index + 1));
  return numbers;
}

export function personalPlaylistOrderWithSelectionPinnedToFront(opts: {
  playlists: readonly PersonalMusicPlaylist[];
  selectedPlaylistIds: readonly string[];
}): string[] | null {
  const currentOrder = opts.playlists.map((playlist) => playlist.id);
  return _orderWithSelectionPinnedToFront(currentOrder, uniquePersonalPlaylistIds(opts.selectedPlaylistIds));
}

export function personalPlaylistItemOrderWithSelectionPinnedToFront(opts: {
  items: Iterable<PersonalMusicPlaylistItem>;
  selectedItemIds: Iterable<string>;
}): string[] | null {
  const currentOrder = Array.from(opts.items, (item) => item.id);
  return _orderWithSelectionPinnedToFront(currentOrder, uniquePersonalPlaylistItemIds(opts.selectedItemIds));
}

function _orderWithSelectionPinnedToFront(currentOrder: readonly string[], uniqueSelected: readonly string[]): string[] | null {
  const currentSet = new Set(currentOrder);
  const selectedOrder = uniqueSelected.filter((id) => currentSet.has(id));
  if (selectedOrder.length === 0) return null;
  const selectedSet = new Set(selectedOrder);
  const nextOrder = [...selectedOrder, ...currentOrder.filter((id) => !selectedSet.has(id))];
  for (let index = 0; index < currentOrder.length; index += 1) {
    if (currentOrder[index] !== nextOrder[index]) return nextOrder;
  }
  return null;
}

export function personalPlaylistArtistsLabel(artists: readonly string[]): string {
  const normalized = artists.map((artist) => artist.trim()).filter((artist) => artist.length > 0);
  return normalized.length === 0 ? '未知歌手' : normalized.join('、');
}

export function personalPlaylistSourceLabel(source: string): string {
  switch (source) {
    case 'netease':
      return '网易云';
    case 'bilibili':
      return '哔哩哔哩';
    case 'tencent':
      return 'QQ音乐';
    default:
      return source.trim().length === 0 ? '未知来源' : source;
  }
}
