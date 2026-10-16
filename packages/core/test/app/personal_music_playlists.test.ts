import { describe, expect, it } from 'vitest';

import {
  PersonalMusicPlaylistsController,
  filteredPersonalMusicPlaylists,
  normalizedPersonalPlaylistName,
  personalPlaylistArtistsLabel,
  personalPlaylistCount1To10,
  personalPlaylistCountAll,
  personalPlaylistCountOver100,
  personalPlaylistFilterActive,
  personalPlaylistItemOrderWithSelectionPinnedToFront,
  personalPlaylistListFilterActive,
  personalPlaylistOrderWithSelectionPinnedToFront,
  personalPlaylistSelectionNumbers,
  personalPlaylistSourceLabel,
  toggledPersonalPlaylistListSelection,
  toggledPersonalPlaylistSelection,
  toggledVisiblePersonalPlaylistSelection,
  uniquePersonalPlaylistItemIds,
} from '../../src/app/personal_music_playlists';
import type { PersonalMusicPlaylistApi, RoomMusicPlaylistApi } from '../../src/protocol/api_client';
import {
  PersonalMusicPlaylist,
  PersonalMusicPlaylistBatchAddResult,
  PersonalMusicPlaylistItem,
  PersonalMusicPlaylistItemsPage,
  PersonalMusicPlaylistMergeResult,
} from '../../src/protocol/models';

describe('personal music playlist helpers', () => {
  it('playlist names are trimmed and limited to 64 characters', () => {
    expect(normalizedPersonalPlaylistName('  夜晚  ')).toBe('夜晚');
    expect(normalizedPersonalPlaylistName('   ')).toBeNull();
    expect(normalizedPersonalPlaylistName('歌'.repeat(65))).toBeNull();
    expect(normalizedPersonalPlaylistName('🎵'.repeat(64))).toBe('🎵'.repeat(64));
  });

  it('playlist item ids are normalized without changing order', () => {
    expect(uniquePersonalPlaylistItemIds([' item_2 ', '', 'item_1', 'item_2'])).toEqual(['item_2', 'item_1']);
  });

  it('selection toggle is immutable', () => {
    const original = new Set(['item_1']);
    const added = toggledPersonalPlaylistSelection(original, 'item_2');
    const removed = toggledPersonalPlaylistSelection(added, 'item_1');

    expect(original).toEqual(new Set(['item_1']));
    expect(added).toEqual(new Set(['item_1', 'item_2']));
    expect(removed).toEqual(new Set(['item_2']));
    expect(toggledPersonalPlaylistListSelection(['a', 'b'], 'a')).toEqual(['b']);
    expect(toggledPersonalPlaylistListSelection(['a'], 'b')).toEqual(['a', 'b']);
  });

  it('playlist filters and labels share one presentation rule', () => {
    expect(personalPlaylistFilterActive({ keyword: '晴天', source: '' })).toBe(true);
    expect(personalPlaylistFilterActive({ keyword: ' ', source: 'netease' })).toBe(true);
    expect(personalPlaylistFilterActive({ keyword: ' ', source: '' })).toBe(false);
    expect(personalPlaylistArtistsLabel([' 周杰伦 ', ''])).toBe('周杰伦');
    expect(personalPlaylistArtistsLabel([])).toBe('未知歌手');
    expect(personalPlaylistSourceLabel('netease')).toBe('网易云');
    expect(personalPlaylistSourceLabel(' ')).toBe('未知来源');
    expect(personalPlaylistSourceLabel('future')).toBe('future');
  });

  it('playlist list filters names and song-count ranges', () => {
    const playlists = [_playlist('empty', '空歌单', 0), _playlist('short', '夜晚精选', 10), _playlist('long', '夜晚长单', 101)];

    expect(
      filteredPersonalMusicPlaylists(playlists, { keyword: ' 夜晚 ', countFilter: personalPlaylistCount1To10 }).map(
        (playlist) => playlist.id,
      ),
    ).toEqual(['short']);
    expect(
      filteredPersonalMusicPlaylists(playlists, { keyword: '', countFilter: personalPlaylistCountOver100 }).map(
        (playlist) => playlist.id,
      ),
    ).toEqual(['long']);
    expect(personalPlaylistListFilterActive({ keyword: ' ', countFilter: personalPlaylistCountAll })).toBe(false);
  });

  it('visible playlist selection preserves hidden selections and order', () => {
    const visible = [_playlist('visible_1', '一', 1), _playlist('visible_2', '二', 2)];
    const original = ['hidden'];
    const selected = toggledVisiblePersonalPlaylistSelection({ selectedPlaylistIds: original, visiblePlaylists: visible });
    const cleared = toggledVisiblePersonalPlaylistSelection({ selectedPlaylistIds: selected, visiblePlaylists: visible });

    expect(original).toEqual(['hidden']);
    expect(selected).toEqual(['hidden', 'visible_1', 'visible_2']);
    expect(personalPlaylistSelectionNumbers(selected)).toEqual(
      new Map([
        ['hidden', 1],
        ['visible_1', 2],
        ['visible_2', 3],
      ]),
    );
    expect(cleared).toEqual(['hidden']);
  });

  it('batch pin follows selection order and preserves unselected order', () => {
    const playlists = [_playlist('first', '一', 0), _playlist('second', '二', 0), _playlist('third', '三', 0)];

    expect(
      personalPlaylistOrderWithSelectionPinnedToFront({ playlists, selectedPlaylistIds: ['third', 'second'] }),
    ).toEqual(['third', 'second', 'first']);
    expect(personalPlaylistOrderWithSelectionPinnedToFront({ playlists, selectedPlaylistIds: ['first', 'second'] })).toBeNull();
    expect(
      personalPlaylistOrderWithSelectionPinnedToFront({ playlists, selectedPlaylistIds: ['missing', ' third ', 'third'] }),
    ).toEqual(['third', 'first', 'second']);
  });

  it('batch item pin follows selection order', () => {
    expect(
      personalPlaylistItemOrderWithSelectionPinnedToFront({ items: _items, selectedItemIds: ['third', 'second'] }),
    ).toEqual(['third', 'second', 'first']);
    expect(
      personalPlaylistItemOrderWithSelectionPinnedToFront({ items: _items, selectedItemIds: ['first', 'second'] }),
    ).toBeNull();
  });
});

describe('PersonalMusicPlaylistsController', () => {
  it('item pin loads every page before submitting the complete order', async () => {
    const api = new PagingPlaylistApi();
    let changes = 0;
    const controller = new PersonalMusicPlaylistsController(api.asApi(), { onChanged: () => (changes += 1) });

    await controller.pinItems({ playlistId: 'playlist', selectedItemIds: ['third', 'second'] });

    expect(api.requestedPages).toEqual([1, 2]);
    expect(api.reorderedItemIds).toEqual(['third', 'second', 'first']);
    expect(changes).toBe(1);
  });

  it('item pin rejects incomplete pagination', async () => {
    const api = new PagingPlaylistApi();
    api.brokenPagination = true;
    const controller = new PersonalMusicPlaylistsController(api.asApi());

    await expect(controller.pinItems({ playlistId: 'playlist', selectedItemIds: ['third'] })).rejects.toThrow(
      '歌单分页数据不完整，请刷新后重试',
    );
    expect(api.reorderedItemIds).toBeNull();
  });

  it('playlist merge validates input and preserves selection order', async () => {
    const api = new MergePlaylistApi();
    let changes = 0;
    const controller = new PersonalMusicPlaylistsController(api.asApi(), { onChanged: () => (changes += 1) });

    expect(controller.canMergePlaylists).toBe(true);
    expect(await controller.mergePlaylists({ name: '  合并结果  ', playlistIds: [' second ', 'first'] })).not.toBeNull();
    expect(api.mergeName).toBe('合并结果');
    expect(api.mergePlaylistIds).toEqual(['second', 'first']);
    expect(changes).toBe(1);

    api.mergeName = null;
    expect(await controller.mergePlaylists({ name: '无效', playlistIds: ['first', 'first'] })).toBeNull();
    expect(api.mergeName).toBeNull();
    expect(changes).toBe(1);
    expect(new PersonalMusicPlaylistsController(new PagingPlaylistApi().asApi()).canMergePlaylists).toBe(false);
  });

  it('batch add validates scope and preserves selected item order', async () => {
    const api = new BatchAddPlaylistApi();
    const controller = new PersonalMusicPlaylistsController(api.asApi());

    expect(controller.canBatchAddItems).toBe(true);
    expect(
      await controller.batchAddItems({
        sourcePlaylistId: ' source ',
        targetPlaylistId: ' target ',
        itemIds: [' second ', 'first'],
      }),
    ).not.toBeNull();
    expect(api.sourcePlaylistId).toBe('source');
    expect(api.targetPlaylistId).toBe('target');
    expect(api.itemIds).toEqual(['second', 'first']);

    api.sourcePlaylistId = null;
    expect(
      await controller.batchAddItems({ sourcePlaylistId: 'same', targetPlaylistId: 'same', itemIds: ['first'] }),
    ).toBeNull();
    expect(
      await controller.batchAddItems({ sourcePlaylistId: 'source', targetPlaylistId: 'target', itemIds: ['first', 'first'] }),
    ).toBeNull();
    expect(api.sourcePlaylistId).toBeNull();
    expect(new PersonalMusicPlaylistsController(new PagingPlaylistApi().asApi()).canBatchAddItems).toBe(false);
  });

  it('move rejects deltas other than -1 and 1', async () => {
    const controller = new PersonalMusicPlaylistsController(new PagingPlaylistApi().asApi());
    await expect(controller.movePlaylist({ playlistId: 'playlist', delta: 2 })).rejects.toThrow('must be -1 or 1');
    await expect(controller.moveItem({ playlistId: 'playlist', itemId: 'first', delta: 0 })).rejects.toThrow(
      'must be -1 or 1',
    );
  });

  it('unavailable controllers are read-only no-ops', async () => {
    const controller = new PersonalMusicPlaylistsController(null);
    expect(controller.available).toBe(false);
    expect(controller.api).toBeNull();
    expect(controller.capabilities.canCreatePlaylists).toBe(false);
    expect(await controller.loadPlaylists()).toBeNull();
    expect(await controller.searchTracks({ keyword: ' ', source: 'netease' })).toEqual([]);
    expect(await controller.searchTracks({ keyword: '晴天', source: 'netease' })).toBeNull();
  });

  it('room-scoped controllers expose stable identities and permission gates', async () => {
    const roomApi = new RoomPlaylistApi();
    const personalApi = new PagingPlaylistApi();
    const make = (canManage: boolean, roomId = 'room_1') =>
      PersonalMusicPlaylistsController.room({
        roomApi: roomApi.asApi(),
        personalApi: personalApi.asApi(),
        roomId,
        canManage,
        searchTracks: async () => [],
      });

    const manager = make(true);
    expect(manager.roomScoped).toBe(true);
    expect(manager.sourceRoomId).toBe('room_1');
    expect(manager.api).toBe(make(true).api);
    expect(manager.api).not.toBe(make(false).api);
    expect(manager.api).not.toBe(make(true, 'room_2').api);
    expect(manager.canImportPersonalPlaylist).toBe(true);
    expect(manager.canCloneRoomPlaylistsToPersonal).toBe(false);
    expect(manager.capabilities.canManagePlaylists).toBe(true);

    await manager.createPlaylist(' 房间歌单 ', { importPlaylistId: ' personal_1 ' });
    expect(roomApi.imported).toEqual({ roomId: 'room_1', name: '房间歌单', importPlaylistId: 'personal_1' });

    const viewer = make(false);
    expect(viewer.canImportPersonalPlaylist).toBe(false);
    expect(viewer.capabilities.canManageItems).toBe(false);
    await expect(viewer.deletePlaylist('playlist')).rejects.toThrow('当前账号没有管理房间歌单的权限');
    await expect(viewer.cloneRoomPlaylistToPersonal('playlist')).rejects.toThrow('当前账号没有管理房间歌单的权限');
  });
});

function _playlist(id: string, name: string, itemCount: number): PersonalMusicPlaylist {
  return new PersonalMusicPlaylist({
    id,
    name,
    description: '',
    revision: 1,
    itemCount,
    createdAt: null,
    updatedAt: null,
  });
}

function _item(id: string, title: string, sortOrder: number): PersonalMusicPlaylistItem {
  return new PersonalMusicPlaylistItem({
    id,
    playlistId: 'playlist',
    trackId: `track_${sortOrder / 10}`,
    source: 'netease',
    title,
    artists: [],
    durationMs: 0,
    sortOrder,
    createdAt: null,
  });
}

const _items = [_item('first', '一', 10), _item('second', '二', 20), _item('third', '三', 30)];

/** Port of `_PagingPlaylistApi implements PersonalMusicPlaylistApi` (noSuchMethod for the rest). */
class PagingPlaylistApi {
  static readonly playlist = _playlist('playlist', '测试歌单', 3);

  readonly requestedPages: number[] = [];
  reorderedItemIds: string[] | null = null;
  brokenPagination = false;

  async getPersonalMusicPlaylist(opts: {
    playlistId: string;
    page?: number;
    pageSize?: number;
  }): Promise<PersonalMusicPlaylistItemsPage> {
    const page = opts.page ?? 1;
    this.requestedPages.push(page);
    const pageItems = this.brokenPagination ? [] : page === 1 ? _items.slice(0, 2) : _items.slice(2);
    return new PersonalMusicPlaylistItemsPage({
      playlist: PagingPlaylistApi.playlist,
      items: pageItems,
      page,
      pageSize: 2,
      total: _items.length,
      hasMore: page === 1,
    });
  }

  async reorderPersonalMusicPlaylistItems(opts: { playlistId: string; itemIds: string[] }): Promise<void> {
    this.reorderedItemIds = [...opts.itemIds];
  }

  asApi(): PersonalMusicPlaylistApi {
    // Only the members exercised by these tests exist on the fake.
    return this as unknown as PersonalMusicPlaylistApi;
  }
}

class MergePlaylistApi extends PagingPlaylistApi {
  mergeName: string | null = null;
  mergePlaylistIds: string[] | null = null;

  async mergePersonalMusicPlaylists(opts: { name: string; playlistIds: string[] }): Promise<PersonalMusicPlaylistMergeResult> {
    this.mergeName = opts.name;
    this.mergePlaylistIds = [...opts.playlistIds];
    return new PersonalMusicPlaylistMergeResult({
      playlist: _playlist('merged', '合并结果', 2),
      sourceItemCount: 2,
      uniqueItemCount: 2,
      duplicateCount: 0,
      itemCount: 2,
      omittedCount: 0,
      deletedPlaylistCount: 2,
      retainedPlaylistCount: 0,
      consumedSourceItemCount: 2,
      truncated: false,
    });
  }
}

class BatchAddPlaylistApi extends PagingPlaylistApi {
  sourcePlaylistId: string | null = null;
  targetPlaylistId: string | null = null;
  itemIds: string[] | null = null;

  async batchAddPersonalMusicPlaylistItems(opts: {
    sourcePlaylistId: string;
    targetPlaylistId: string;
    itemIds: string[];
  }): Promise<PersonalMusicPlaylistBatchAddResult> {
    this.sourcePlaylistId = opts.sourcePlaylistId;
    this.targetPlaylistId = opts.targetPlaylistId;
    this.itemIds = [...opts.itemIds];
    return new PersonalMusicPlaylistBatchAddResult({
      playlist: _playlist('target', '目标歌单', 3),
      selectedItemCount: 2,
      uniqueItemCount: 2,
      duplicateCount: 0,
      alreadyPresentCount: 0,
      addedItemCount: 2,
      omittedCount: 0,
      truncated: false,
    });
  }
}

class RoomPlaylistApi {
  imported: { roomId: string; name: string; importPlaylistId: string } | null = null;

  async createRoomMusicPlaylistFromPersonal(opts: {
    roomId: string;
    name: string;
    importPlaylistId: string;
  }): Promise<PersonalMusicPlaylist> {
    this.imported = { roomId: opts.roomId, name: opts.name, importPlaylistId: opts.importPlaylistId };
    return _playlist('room_playlist', opts.name, 0);
  }

  async deleteRoomMusicPlaylist(opts: { roomId: string; playlistId: string }): Promise<void> {}

  asApi(): RoomMusicPlaylistApi {
    return this as unknown as RoomMusicPlaylistApi;
  }
}
