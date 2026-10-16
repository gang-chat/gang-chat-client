import { MusicTrackPreviewController, type MusicTrackPreviewPlatformFactory } from '@gang-chat/core/app/music_track_preview';
import {
  filteredPersonalMusicPlaylists,
  personalPlaylistAllVisibleSelected,
  personalPlaylistFilterActive,
  personalPlaylistItemOrderWithSelectionPinnedToFront,
  personalPlaylistListFilterActive,
  personalPlaylistOrderWithSelectionPinnedToFront,
  personalPlaylistSelectionNumbers,
  personalPlaylistCountAll,
  toggledPersonalPlaylistListSelection,
  toggledPersonalPlaylistSelection,
  toggledVisiblePersonalPlaylistSelection,
  type MusicPlaylistManagementCapabilities,
  type PersonalMusicPlaylistsController,
  type PersonalPlaylistCreateDraft,
  type PersonalPlaylistFilterDraft,
  type PersonalPlaylistItemFilterDraft,
} from '@gang-chat/core/app/personal_music_playlists';
import { scheduleMicrotask, unawaited } from '@gang-chat/core/lib/async';
import { ApiException, newUuid, type GangApi, type MusicTrackPreviewApi } from '@gang-chat/core/protocol/api_client';
import {
  MessageAttachment,
  MusicBoxSearchResult,
  type CurrentUser,
  type PersonalMusicPlaylist,
  type PersonalMusicPlaylistBatchAddResult,
  type PersonalMusicPlaylistItem,
  type PersonalMusicPlaylistMergeResult,
  type RoomCard,
} from '@gang-chat/core/protocol/models';

import type { ReactNode } from 'react';

import { Center, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { useViewState, ViewState } from '../lib/view_state';
import { Button, ButtonIconPlain, ButtonTone } from '../ui/button';
import { showFloatingErrorNotice, showFloatingSuccessNotice } from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { showDialog } from '../ui/overlay';
import { SettingsFixedHeaderCard, SettingsList } from '../ui/settings_scaffold';
import { StickerActionGrid, StickerActionGridEntry, StickerConfirmDialog } from '../ui/sticker_manager';
import { MusicTrackPlaylistTarget } from '../home/music_track_profile_card';
import { _SettingsEmptyState } from './settings_profile_widgets';
import { _cyan, _textMuted, _textPrimary } from './settings_page';
import {
  _MusicPlaylistFilterDialog,
  _MusicPlaylistImportDialog,
  _MusicPlaylistItemFilterDialog,
  _MusicPlaylistNameDialog,
  _MusicPlaylistShareRoomDialog,
  _MusicPlaylistTrackSearchDialog,
} from './settings_music_playlists_dialogs';
import { _MusicPlaylistErrorText, _MusicPlaylistItemTile, _MusicPlaylistSummaryList } from './settings_music_playlists_tiles';

// Dart `part of 'settings_page.dart'`. The rows and dialogs live in sibling
// modules; everything is re-exported here under its Dart name.
export * from './settings_music_playlists_tiles';
export * from './settings_music_playlists_dialogs';

export interface MusicPlaylistsPanelProps {
  controller: PersonalMusicPlaylistsController;
  initialPlaylistId?: string | null;
  reloadToken?: number;
  unavailableMessage: string;
  onLoadingChanged?: ((loading: boolean) => void) | null;
  title?: string;
  previewApi?: MusicTrackPreviewApi | null;
  previewPlatformFactory?: MusicTrackPreviewPlatformFactory | null;
  shareApi?: GangApi | null;
  currentUser?: CurrentUser | null;
}

/**
 * PORT: Dart's `throw StateError(message)`; `toString()` keeps the Dart
 * "Bad state: …" wording that ends up in the error text.
 */
class _StateError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'StateError';
  }

  override toString(): string {
    return `Bad state: ${this.message}`;
  }
}

/** Dart `api is MusicTrackPreviewApi`. */
function _isMusicTrackPreviewApi(value: unknown): value is MusicTrackPreviewApi {
  return (
    value != null &&
    typeof value === 'object' &&
    typeof (value as Partial<MusicTrackPreviewApi>).downloadMusicTrackPreview === 'function'
  );
}

/** PORT: `WidgetsBinding.instance.addPostFrameCallback` → the next animation frame. */
function _addPostFrameCallback(callback: () => void): void {
  requestAnimationFrame(() => callback());
}

export class _MusicPlaylistsPanelState extends ViewState<MusicPlaylistsPanelProps> {
  _playlists: PersonalMusicPlaylist[] = [];
  _activePlaylist: PersonalMusicPlaylist | null = null;
  _items: PersonalMusicPlaylistItem[] = [];
  _selectedPlaylistIds: string[] = [];
  _selectedItemIds: Set<string> = new Set();

  _playlistFilterKeyword = '';
  _playlistCountFilter = personalPlaylistCountAll;
  _filterSource = '';
  _appliedFilterKeyword = '';
  _itemsPage = 1;
  _itemTotal = 0;
  _maxPlaylists = 50;
  _maxPlaylistItems = 500;
  _itemsHaveMore = false;
  _loading = false;
  _loadingItems = false;
  _loadingMore = false;
  _creating = false;
  _deletingPlaylists = false;
  _pinningPlaylists = false;
  _mergingPlaylists = false;
  _cloningPlaylistId: string | null = null;
  _sharingPlaylistId: string | null = null;
  _sharingItemId: string | null = null;
  _deletingItems = false;
  _addingSelectedItems = false;
  _pinningItems = false;
  _managingPlaylists = false;
  _managingItems = false;
  _error: string | null = null;
  readonly _busyPlaylistIds = new Set<string>();
  readonly _busyItemIds = new Set<string>();
  _playlistLoadGeneration = 0;
  _itemLoadGeneration = 0;
  _previewController: MusicTrackPreviewController | null = null;
  _handledInitialPlaylistId: string | null = null;

  get _capabilities(): MusicPlaylistManagementCapabilities {
    return this.widget.controller.capabilities;
  }

  get _filterActive(): boolean {
    return personalPlaylistFilterActive({ keyword: this._appliedFilterKeyword, source: this._filterSource });
  }

  get _playlistFilterActive(): boolean {
    return personalPlaylistListFilterActive({ keyword: this._playlistFilterKeyword, countFilter: this._playlistCountFilter });
  }

  get _playlistManagementBusy(): boolean {
    return (
      this._loading ||
      this._creating ||
      this._deletingPlaylists ||
      this._pinningPlaylists ||
      this._mergingPlaylists ||
      this._cloningPlaylistId != null ||
      this._sharingPlaylistId != null ||
      this._busyPlaylistIds.size > 0
    );
  }

  get _busy(): boolean {
    return (
      this._loading ||
      this._loadingItems ||
      this._loadingMore ||
      this._creating ||
      this._deletingPlaylists ||
      this._pinningPlaylists ||
      this._mergingPlaylists ||
      this._cloningPlaylistId != null ||
      this._busyPlaylistIds.size > 0 ||
      this._deletingItems ||
      this._addingSelectedItems ||
      this._pinningItems ||
      this._sharingItemId != null
    );
  }

  override initState(): void {
    this._previewController = this._createPreviewController();
    scheduleMicrotask(() => unawaited(this._loadPlaylists()));
  }

  override didUpdateProps(oldWidget: MusicPlaylistsPanelProps): void {
    const widget = this.widget;
    if (
      oldWidget.controller.api !== widget.controller.api ||
      (oldWidget.previewApi ?? null) !== (widget.previewApi ?? null) ||
      (oldWidget.previewPlatformFactory ?? null) !== (widget.previewPlatformFactory ?? null)
    ) {
      const previous = this._previewController;
      this._previewController = this._createPreviewController();
      if (previous != null) unawaited(previous.dispose());
      // Dart rebuilt right after didUpdateWidget; the React render already ran.
      this.setState();
    }
    if ((oldWidget.reloadToken ?? 0) !== (widget.reloadToken ?? 0) || oldWidget.controller.api !== widget.controller.api) {
      this._handledInitialPlaylistId = null;
      this._managingPlaylists = false;
      this._managingItems = false;
      this._selectedPlaylistIds = [];
      this._selectedItemIds = new Set();
      this.setState();
      scheduleMicrotask(() => unawaited(this._loadPlaylists()));
    }
    if ((oldWidget.initialPlaylistId ?? null) !== (widget.initialPlaylistId ?? null)) {
      this._handledInitialPlaylistId = null;
      scheduleMicrotask(() => unawaited(this._loadPlaylists()));
    }
  }

  override dispose(): void {
    this._playlistLoadGeneration += 1;
    this._itemLoadGeneration += 1;
    const preview = this._previewController;
    if (preview != null) unawaited(preview.dispose());
  }

  _createPreviewController(): MusicTrackPreviewController | null {
    const api = this.widget.previewApi ?? this.widget.controller.api;
    const factory = this.widget.previewPlatformFactory;
    if (!_isMusicTrackPreviewApi(api) || factory == null) return null;
    return new MusicTrackPreviewController({ api, platform: factory.create() });
  }

  _setLoading(loading: boolean): void {
    if (!this.mounted || this._loading === loading) return;
    this.setState(() => {
      this._loading = loading;
    });
    this.widget.onLoadingChanged?.(loading);
  }

  _loadPlaylists = async (): Promise<void> => {
    if (!this.widget.controller.available) return;
    const generation = ++this._playlistLoadGeneration;
    this._setLoading(true);
    if (this.mounted) {
      this.setState(() => {
        this._error = null;
      });
    }
    try {
      const result = await this.widget.controller.loadPlaylists();
      if (!this.mounted || generation !== this._playlistLoadGeneration || result == null) {
        return;
      }
      const activeID = this._activePlaylist?.id ?? null;
      let refreshedActive: PersonalMusicPlaylist | null = null;
      if (activeID != null) {
        for (const playlist of result.playlists) {
          if (playlist.id === activeID) {
            refreshedActive = playlist;
            break;
          }
        }
      }
      if (activeID != null && refreshedActive == null) {
        this._itemLoadGeneration += 1;
      }
      this.setState(() => {
        this._playlists = result.playlists;
        const playlistIDs = new Set(result.playlists.map((playlist) => playlist.id));
        this._selectedPlaylistIds = this._selectedPlaylistIds.filter((id) => playlistIDs.has(id));
        this._maxPlaylists = result.maxPlaylists;
        this._maxPlaylistItems = result.maxPlaylistItems;
        this._activePlaylist = refreshedActive;
        if (activeID != null && refreshedActive == null) {
          this._items = [];
          this._selectedItemIds = new Set();
          this._managingItems = false;
          this._loadingItems = false;
          this._loadingMore = false;
        }
      });
      if (refreshedActive != null) {
        scheduleMicrotask(() => unawaited(this._loadItems({ reset: true })));
      } else {
        const initialId = this.widget.initialPlaylistId?.trim() ?? null;
        if (initialId != null && initialId.length > 0 && this._handledInitialPlaylistId !== initialId) {
          this._handledInitialPlaylistId = initialId;
          let initialPlaylist: PersonalMusicPlaylist | null = null;
          for (const playlist of result.playlists) {
            if (playlist.id === initialId) {
              initialPlaylist = playlist;
              break;
            }
          }
          if (initialPlaylist != null) {
            const target = initialPlaylist;
            scheduleMicrotask(() => unawaited(this._openPlaylist(target)));
          }
        }
      }
    } catch (error) {
      if (!this.mounted || generation !== this._playlistLoadGeneration) return;
      this.setState(() => {
        this._error = String(error);
      });
    } finally {
      if (this.mounted && generation === this._playlistLoadGeneration) {
        this._setLoading(false);
      }
    }
  };

  async _openPlaylist(playlist: PersonalMusicPlaylist): Promise<void> {
    this.setState(() => {
      this._activePlaylist = playlist;
      this._items = [];
      this._selectedItemIds = new Set();
      this._managingItems = false;
      this._error = null;
      this._appliedFilterKeyword = '';
      this._filterSource = '';
    });
    await this._loadItems({ reset: true });
  }

  _closePlaylist = (): void => {
    this._itemLoadGeneration += 1;
    const preview = this._previewController;
    if (preview != null) unawaited(preview.stop());
    this.setState(() => {
      this._activePlaylist = null;
      this._items = [];
      this._selectedItemIds = new Set();
      this._managingItems = false;
      this._loadingItems = false;
      this._loadingMore = false;
      this._error = null;
      this._appliedFilterKeyword = '';
      this._filterSource = '';
    });
  };

  _loadTrackPlaylistTargets = async (): Promise<MusicTrackPlaylistTarget[]> => {
    if (!this.widget.controller.roomScoped) {
      return this._playlists.map((playlist) => MusicTrackPlaylistTarget.personal(playlist));
    }

    const roomId = this.widget.controller.sourceRoomId;
    const targets: MusicTrackPlaylistTarget[] =
      roomId != null ? this._playlists.map((playlist) => MusicTrackPlaylistTarget.room({ playlist, roomId })) : [];
    let personalError: unknown = null;
    let failed = false;
    try {
      const page = await this.widget.controller.loadPersonalPlaylistTargets();
      if (page != null) {
        targets.push(...page.playlists.map((playlist) => MusicTrackPlaylistTarget.personal(playlist)));
      }
    } catch (error) {
      personalError = error;
      failed = true;
    }
    if (targets.length === 0 && failed) throw personalError;
    return targets;
  };

  _addSearchResultToPlaylistTarget = async (track: MusicBoxSearchResult, target: MusicTrackPlaylistTarget): Promise<void> => {
    if (target.roomScoped) {
      if (!this.widget.controller.roomScoped || target.roomId !== this.widget.controller.sourceRoomId) {
        throw new _StateError('目标房间歌单与当前房间不匹配');
      }
      await this._addSearchResultToPlaylist(track, target.playlist);
      return;
    }
    if (!this.widget.controller.roomScoped) {
      await this._addSearchResultToPlaylist(track, target.playlist);
      return;
    }
    const added = await this.widget.controller.addTrackToPersonalPlaylist({ playlistId: target.playlist.id, track });
    if (added == null) throw new _StateError('当前个人歌单不可写');
  };

  _addPlaylistItemToPlaylistTarget(item: PersonalMusicPlaylistItem, target: MusicTrackPlaylistTarget): Promise<void> {
    return this._addSearchResultToPlaylistTarget(
      new MusicBoxSearchResult({ trackId: item.trackId, name: item.title, artists: item.artists, source: item.source }),
      target,
    );
  }

  async _addSearchResultToPlaylist(track: MusicBoxSearchResult, target: PersonalMusicPlaylist): Promise<void> {
    const added = await this.widget.controller.addTrack({ playlistId: target.id, track });
    if (added == null) throw new _StateError('当前歌单不可写');
    if (!this.mounted) return;

    const activeTarget = this._activePlaylist?.id === target.id;
    this.setState(() => {
      this._playlists = this._playlists.map((playlist) =>
        playlist.id === target.id ? playlist.copyWith({ itemCount: playlist.itemCount + 1 }) : playlist,
      );
      if (activeTarget) {
        this._activePlaylist = this._activePlaylist!.copyWith({ itemCount: this._activePlaylist!.itemCount + 1 });
        this._itemTotal += 1;
        if (!this._filterActive && !this._itemsHaveMore) {
          this._items = [...this._items, added];
        } else {
          scheduleMicrotask(() => unawaited(this._loadItems({ reset: true })));
        }
      }
    });
  }

  async _loadItems(opts: { reset: boolean }): Promise<void> {
    const { reset } = opts;
    const playlist = this._activePlaylist;
    if (playlist == null || !this.widget.controller.available) return;
    if (!reset && (this._loadingItems || this._loadingMore || !this._itemsHaveMore)) return;
    const generation = reset ? ++this._itemLoadGeneration : this._itemLoadGeneration;
    const page = reset ? 1 : this._itemsPage + 1;
    this.setState(() => {
      if (reset) {
        this._loadingItems = true;
        this._loadingMore = false;
        this._selectedItemIds = new Set();
      } else {
        this._loadingMore = true;
      }
      this._error = null;
    });
    try {
      const result = await this.widget.controller.loadItems({
        playlistId: playlist.id,
        page,
        keyword: this._appliedFilterKeyword,
        source: this._filterSource,
      });
      if (!this.mounted || generation !== this._itemLoadGeneration || result == null || this._activePlaylist?.id !== playlist.id) {
        return;
      }
      this.setState(() => {
        this._activePlaylist = result.playlist;
        this._items = reset ? result.items : [...this._items, ...result.items];
        this._itemsPage = result.page;
        this._itemTotal = result.total;
        this._itemsHaveMore = result.hasMore;
      });
    } catch (error) {
      if (!this.mounted || generation !== this._itemLoadGeneration) return;
      this.setState(() => {
        this._error = String(error);
      });
    } finally {
      if (this.mounted && generation === this._itemLoadGeneration) {
        this.setState(() => {
          this._loadingItems = false;
          this._loadingMore = false;
        });
      }
    }
  }

  _createPlaylist = async (): Promise<void> => {
    if (!this._capabilities.canCreatePlaylists || this._busy || this._playlists.length >= this._maxPlaylists) {
      return;
    }
    const controller = this.widget.controller;
    const draft = await showDialog<PersonalPlaylistCreateDraft>(() => (
      <_MusicPlaylistNameDialog
        title="新建歌单"
        icon={Icons.playlist_add}
        confirmLabel="创建"
        confirmIcon={Icons.add}
        loadImportPlaylists={controller.canImportPersonalPlaylist ? () => controller.loadImportPlaylists() : null}
      />
    ));
    if (!this.mounted || draft == null) return;
    this.setState(() => {
      this._creating = true;
      this._error = null;
    });
    try {
      await this.widget.controller.createPlaylist(draft.name, { importPlaylistId: draft.importPlaylistId });
      await this._loadPlaylists();
    } catch (error) {
      if (this.mounted) {
        this.setState(() => {
          this._error = String(error);
        });
      }
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._creating = false;
        });
      }
    }
  };

  _renamePlaylist = async (playlist: PersonalMusicPlaylist): Promise<void> => {
    if (!this._capabilities.canRenamePlaylists || this._playlistManagementBusy) return;
    const draft = await showDialog<PersonalPlaylistCreateDraft>(() => (
      <_MusicPlaylistNameDialog
        title="重命名歌单"
        icon={Icons.edit_outlined}
        confirmLabel="重命名"
        confirmIcon={Icons.check}
        initialName={playlist.name}
      />
    ));
    if (!this.mounted || draft == null || draft.name === playlist.name) return;
    this.setState(() => {
      this._busyPlaylistIds.add(playlist.id);
      this._error = null;
    });
    try {
      await this.widget.controller.renamePlaylist({ playlistId: playlist.id, name: draft.name });
      await this._loadPlaylists();
    } catch (error) {
      if (this.mounted) {
        this.setState(() => {
          this._error = String(error);
        });
      }
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._busyPlaylistIds.delete(playlist.id);
        });
      }
    }
  };

  _togglePlaylistManageMode = (): void => {
    if (!this._capabilities.canManagePlaylists || this._playlistManagementBusy) return;
    this.setState(() => {
      this._managingPlaylists = !this._managingPlaylists;
      this._selectedPlaylistIds = [];
    });
  };

  _togglePlaylistSelection(playlistId: string): void {
    if (this._playlistManagementBusy) return;
    this.setState(() => {
      this._selectedPlaylistIds = toggledPersonalPlaylistListSelection(this._selectedPlaylistIds, playlistId);
    });
  }

  _toggleSelectVisiblePlaylists(visiblePlaylists: PersonalMusicPlaylist[]): void {
    if (this._playlistManagementBusy || visiblePlaylists.length === 0) return;
    this.setState(() => {
      this._selectedPlaylistIds = toggledVisiblePersonalPlaylistSelection({
        selectedPlaylistIds: this._selectedPlaylistIds,
        visiblePlaylists,
      });
    });
  }

  _openPlaylistFilter = async (): Promise<void> => {
    if (this._playlistManagementBusy) return;
    const keyword = this._playlistFilterKeyword;
    const countFilter = this._playlistCountFilter;
    const draft = await showDialog<PersonalPlaylistFilterDraft>(() => (
      <_MusicPlaylistFilterDialog keyword={keyword} countFilter={countFilter} />
    ));
    if (!this.mounted || draft == null) return;
    this.setState(() => {
      this._playlistFilterKeyword = draft.keyword;
      this._playlistCountFilter = draft.countFilter;
      this._selectedPlaylistIds = [];
    });
  };

  _deleteSelectedPlaylists = async (): Promise<void> => {
    if (!this._capabilities.canDeletePlaylists || this._playlistManagementBusy || this._selectedPlaylistIds.length === 0) {
      return;
    }
    const selectedIDs = [...this._selectedPlaylistIds];
    const confirmed = await showDialog<boolean>(() => (
      <StickerConfirmDialog
        title="删除歌单"
        body={`确定删除选中的 ${selectedIDs.length} 个歌单吗？歌单中的歌曲记录也会一并删除。`}
        confirmLabel="删除"
        confirmIcon={Icons.delete_outline}
        danger
      />
    ));
    if (!this.mounted || confirmed !== true) return;
    this.setState(() => {
      this._deletingPlaylists = true;
      this._error = null;
    });
    const failedIDs: string[] = [];
    let firstError: unknown = null;
    let hasError = false;
    for (const playlistId of selectedIDs) {
      try {
        await this.widget.controller.deletePlaylist(playlistId);
      } catch (error) {
        failedIDs.push(playlistId);
        if (!hasError) {
          firstError = error;
          hasError = true;
        }
      }
    }
    if (!this.mounted) return;
    await this._loadPlaylists();
    if (!this.mounted) return;
    this.setState(() => {
      this._deletingPlaylists = false;
      if (failedIDs.length === 0) {
        this._managingPlaylists = false;
        this._selectedPlaylistIds = [];
        this._error = null;
      } else {
        const currentIDs = new Set(this._playlists.map((playlist) => playlist.id));
        this._selectedPlaylistIds = failedIDs.filter((id) => currentIDs.has(id));
        const deletedCount = selectedIDs.length - failedIDs.length;
        this._error = `已删除 ${deletedCount} 个歌单，${failedIDs.length} 个删除失败：${String(firstError)}`;
      }
    });
  };

  _pinSelectedPlaylists = async (): Promise<void> => {
    if (
      !this._capabilities.canReorderPlaylists ||
      this._playlistManagementBusy ||
      personalPlaylistOrderWithSelectionPinnedToFront({
        playlists: this._playlists,
        selectedPlaylistIds: this._selectedPlaylistIds,
      }) == null
    ) {
      return;
    }
    const selectedIDs = [...this._selectedPlaylistIds];
    this.setState(() => {
      this._pinningPlaylists = true;
      this._error = null;
    });
    try {
      await this.widget.controller.pinPlaylists(selectedIDs);
      await this._loadPlaylists();
      if (this.mounted) {
        this.setState(() => {
          this._selectedPlaylistIds = [];
        });
      }
    } catch (error) {
      if (this.mounted) {
        this.setState(() => {
          this._error = String(error);
        });
      }
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._pinningPlaylists = false;
        });
      }
    }
  };

  _mergeSelectedPlaylists = async (): Promise<void> => {
    if (!this.widget.controller.canMergePlaylists || this._playlistManagementBusy || this._selectedPlaylistIds.length < 2) {
      return;
    }
    const selectedIDs = [...this._selectedPlaylistIds];
    const draft = await showDialog<PersonalPlaylistCreateDraft>(() => (
      <_MusicPlaylistNameDialog
        title="合并歌单"
        icon={Icons.merge_type}
        confirmLabel="合并"
        confirmIcon={Icons.merge_type}
        description={
          '歌曲会按选择顺序合并并按具体链接去重。已完整合并的来源歌单会删除；' + '超过 500 首时，未合并的剩余歌曲仍保留在原歌单中。'
        }
      />
    ));
    if (!this.mounted || draft == null) return;
    const sourceItemCount = this._playlists
      .filter((playlist) => selectedIDs.includes(playlist.id))
      .reduce((total, playlist) => total + playlist.itemCount, 0);
    const maxPlaylistItems = this._maxPlaylistItems;
    const exceedsItemLimit = sourceItemCount > maxPlaylistItems;
    const confirmed = await showDialog<boolean>(() => (
      <StickerConfirmDialog
        title={exceedsItemLimit ? '歌曲不能全部保留' : '确认合并歌单'}
        body={
          exceedsItemLimit
            ? `所选歌单共有 ${sourceItemCount} 首歌曲，超过单个歌单 ${maxPlaylistItems} 首上限，` +
              '无法全部原样保留。系统会按选择顺序、按具体链接去重，' +
              `合并后的歌单最多保留前 ${maxPlaylistItems} 首；未能合并的剩余歌曲会保留在原歌单中，` +
              '已完整合并的来源歌单会删除。是否继续？'
            : `确定将 ${selectedIDs.length} 个歌单按选择顺序合并为“${draft.name}”吗？` + '已完整合并的来源歌单会删除，此操作无法撤销。'
        }
        confirmLabel={exceedsItemLimit ? '继续合并' : '确认合并'}
        confirmIcon={Icons.merge_type}
      />
    ));
    if (!this.mounted || confirmed !== true) return;
    this.setState(() => {
      this._mergingPlaylists = true;
      this._error = null;
    });
    try {
      const result = await this.widget.controller.mergePlaylists({ name: draft.name, playlistIds: selectedIDs });
      if (!this.mounted || result == null) return;
      await this._loadPlaylists();
      if (!this.mounted) return;
      this.setState(() => {
        this._managingPlaylists = false;
        this._selectedPlaylistIds = [];
      });
      this._showPlaylistMergeResultNotice(result);
    } catch (error) {
      if (this.mounted) {
        showFloatingErrorNotice(`合并歌单失败：${String(error)}`);
      }
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._mergingPlaylists = false;
        });
      }
    }
  };

  _showPlaylistMergeResultNotice(result: PersonalMusicPlaylistMergeResult): void {
    _addPostFrameCallback(() => {
      if (!this.mounted) return;
      if (result.truncated) {
        showFloatingSuccessNotice(
          `已合并为“${result.playlist.name}”，按顺序保留前 ` + `${result.itemCount} 首；其余歌曲已保留在原歌单中`,
        );
        return;
      }
      const duplicateLabel = result.duplicateCount === 0 ? '' : `，已去重 ${result.duplicateCount} 首`;
      showFloatingSuccessNotice(`已合并为“${result.playlist.name}”，共 ${result.itemCount} 首` + `${duplicateLabel}`);
    });
  }

  _cloneRoomPlaylist = async (playlist: PersonalMusicPlaylist): Promise<void> => {
    if (!this.widget.controller.canCloneRoomPlaylistsToPersonal || this._playlistManagementBusy) {
      return;
    }
    const confirmed = await showDialog<boolean>(() => (
      <StickerConfirmDialog
        title="克隆歌单"
        body={`确定将“${playlist.name}”克隆到“我的歌单”吗？` + '克隆后的名称使用“房间备注名 · 歌单名”。'}
        confirmLabel="克隆"
        confirmIcon={Icons.library_add_outlined}
      />
    ));
    if (!this.mounted || confirmed !== true) return;
    this.setState(() => {
      this._cloningPlaylistId = playlist.id;
      this._error = null;
    });
    try {
      const result = await this.widget.controller.cloneRoomPlaylistToPersonal(playlist.id);
      if (!this.mounted || result == null) return;
      this.setState(() => {
        this._cloningPlaylistId = null;
      });
      this._showRoomPlaylistCloneResultNotice(result);
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._cloningPlaylistId = null;
      });
      this._showRoomPlaylistCloneErrorNotice(error);
    } finally {
      if (this.mounted && this._cloningPlaylistId === playlist.id) {
        this.setState(() => {
          this._cloningPlaylistId = null;
        });
      }
    }
  };

  _showRoomPlaylistCloneResultNotice(result: PersonalMusicPlaylist): void {
    // Wait until the confirmation route has finished closing and management
    // mode has rebuilt. This keeps the global notice above the settled page
    // instead of briefly placing it behind the outgoing dialog overlay.
    _addPostFrameCallback(() => {
      if (!this.mounted) return;
      showFloatingSuccessNotice(`已克隆到我的歌单 - ${result.name}`);
    });
  }

  _showRoomPlaylistCloneErrorNotice(error: unknown): void {
    _addPostFrameCallback(() => {
      if (!this.mounted) return;
      const message =
        error instanceof ApiException && error.code === 'playlist_limit_reached' ? '克隆失败：我的歌单已达 50 个上限' : `克隆失败：${String(error)}`;
      showFloatingErrorNotice(message);
    });
  }

  _sharePersonalPlaylist = async (playlist: PersonalMusicPlaylist): Promise<void> => {
    const api = this.widget.shareApi ?? null;
    if (api == null || this.widget.controller.roomScoped || this._playlistManagementBusy) {
      return;
    }
    const currentUser = this.widget.currentUser ?? null;
    const room = await showDialog<RoomCard>(() => (
      <_MusicPlaylistShareRoomDialog api={api} playlistName={playlist.name} currentUser={currentUser} />
    ));
    if (!this.mounted || room == null) return;
    this.setState(() => {
      this._sharingPlaylistId = playlist.id;
      this._error = null;
    });
    try {
      await api.sendMessage({
        roomId: room.id,
        clientMessageId: newUuid(),
        body: '',
        type: 'playlist',
        attachments: [new MessageAttachment({ type: 'playlist', playlistId: playlist.id })],
      });
      if (!this.mounted) return;
      showFloatingSuccessNotice(`已将“${playlist.name}”分享到“${room.displayName}”`);
    } catch (error) {
      if (this.mounted) showFloatingErrorNotice(`分享歌单失败：${String(error)}`);
    } finally {
      if (this.mounted && this._sharingPlaylistId === playlist.id) {
        this.setState(() => {
          this._sharingPlaylistId = null;
        });
      }
    }
  };

  _sharePlaylistItem = async (item: PersonalMusicPlaylistItem): Promise<void> => {
    const api = this.widget.shareApi ?? null;
    const playlist = this._activePlaylist;
    if (api == null || playlist == null || this._sharingItemId != null) return;
    const currentUser = this.widget.currentUser ?? null;
    const room = await showDialog<RoomCard>(() => (
      <_MusicPlaylistShareRoomDialog
        api={api}
        playlistName={item.title}
        currentUser={currentUser}
        dialogTitle="分享歌曲"
        itemLabel="歌曲"
        keyPrefix="music-track-share-room"
      />
    ));
    if (!this.mounted || room == null) return;
    this.setState(() => {
      this._sharingItemId = item.id;
      this._error = null;
    });
    try {
      await api.sendMessage({
        roomId: room.id,
        clientMessageId: newUuid(),
        body: '',
        type: 'music_track',
        attachments: [
          new MessageAttachment({
            type: 'music_track',
            playlistId: playlist.id,
            playlistScope: this.widget.controller.roomScoped ? 'room' : 'personal',
            sourceRoomId: this.widget.controller.sourceRoomId,
            itemId: item.id,
          }),
        ],
      });
      if (!this.mounted) return;
      showFloatingSuccessNotice(`已将“${item.title}”分享到“${room.displayName}”`);
    } catch (error) {
      if (this.mounted) showFloatingErrorNotice(`分享歌曲失败：${String(error)}`);
    } finally {
      if (this.mounted && this._sharingItemId === item.id) {
        this.setState(() => {
          this._sharingItemId = null;
        });
      }
    }
  };

  _movePlaylist = async (playlist: PersonalMusicPlaylist, delta: number): Promise<void> => {
    if (!this._capabilities.canReorderPlaylists || this._playlistManagementBusy || this._playlistFilterActive) {
      return;
    }
    this.setState(() => {
      this._busyPlaylistIds.add(playlist.id);
      this._error = null;
    });
    try {
      await this.widget.controller.movePlaylist({ playlistId: playlist.id, delta });
      await this._loadPlaylists();
    } catch (error) {
      if (this.mounted) {
        this.setState(() => {
          this._error = String(error);
        });
      }
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._busyPlaylistIds.delete(playlist.id);
        });
      }
    }
  };

  _openOrSelectPlaylist = (playlist: PersonalMusicPlaylist): void => {
    if (this._managingPlaylists) {
      this._togglePlaylistSelection(playlist.id);
      return;
    }
    unawaited(this._openPlaylist(playlist));
  };

  _openTrackSearchDialog = async (): Promise<void> => {
    const playlist = this._activePlaylist;
    if (!this._capabilities.canAddItems || playlist == null || this._busy) return;
    const controller = this.widget.controller;
    const previewController = this._previewController;
    await showDialog<void>(() => (
      <_MusicPlaylistTrackSearchDialog
        controller={controller}
        playlistId={playlist.id}
        previewController={previewController}
        loadPlaylistTargets={this._loadTrackPlaylistTargets}
        onAddToPlaylistTarget={this._addSearchResultToPlaylistTarget}
        onTrackAdded={this._refreshAfterTrackAdded}
      />
    ));
  };

  _refreshAfterTrackAdded = async (): Promise<void> => {
    if (!this.mounted || this._activePlaylist == null) return;
    await this._loadItems({ reset: true });
    await this._reloadPlaylistSummariesWithoutClosing();
  };

  async _reloadPlaylistSummariesWithoutClosing(): Promise<void> {
    const result = await this.widget.controller.loadPlaylists();
    if (!this.mounted || result == null) return;
    const activeID = this._activePlaylist?.id ?? null;
    this.setState(() => {
      this._playlists = result.playlists;
      this._maxPlaylists = result.maxPlaylists;
      this._maxPlaylistItems = result.maxPlaylistItems;
      if (activeID != null) {
        for (const playlist of result.playlists) {
          if (playlist.id === activeID) {
            this._activePlaylist = playlist;
            break;
          }
        }
      }
    });
  }

  _openItemFilterDialog = async (): Promise<void> => {
    if (this._busy) return;
    const keyword = this._appliedFilterKeyword;
    const source = this._filterSource;
    const draft = await showDialog<PersonalPlaylistItemFilterDraft>(() => (
      <_MusicPlaylistItemFilterDialog keyword={keyword} source={source} />
    ));
    if (!this.mounted || draft == null) return;
    if (draft.keyword === this._appliedFilterKeyword && draft.source === this._filterSource) {
      return;
    }
    this.setState(() => {
      this._appliedFilterKeyword = draft.keyword;
      this._filterSource = draft.source;
      this._selectedItemIds = new Set();
    });
    await this._loadItems({ reset: true });
  };

  _toggleManagingItems = (): void => {
    if (!this._capabilities.canManageItems || this._busy) return;
    this.setState(() => {
      this._managingItems = !this._managingItems;
      this._selectedItemIds = new Set();
    });
  };

  _toggleItemSelection(itemID: string): void {
    if (!this._managingItems || this._deletingItems || this._addingSelectedItems || this._pinningItems) {
      return;
    }
    this.setState(() => {
      this._selectedItemIds = toggledPersonalPlaylistSelection(this._selectedItemIds, itemID);
    });
  }

  _toggleSelectVisible = (): void => {
    if (this._addingSelectedItems || this._deletingItems || this._pinningItems) return;
    const visibleIDs = new Set(this._items.map((item) => item.id));
    const allSelected = visibleIDs.size > 0 && [...visibleIDs].every((id) => this._selectedItemIds.has(id));
    this.setState(() => {
      const next = new Set(this._selectedItemIds);
      if (allSelected) {
        for (const id of visibleIDs) next.delete(id);
      } else {
        for (const id of visibleIDs) next.add(id);
      }
      this._selectedItemIds = next;
    });
  };

  _deleteSelectedItems = async (): Promise<void> => {
    const playlist = this._activePlaylist;
    if (
      !this._capabilities.canDeleteItems ||
      playlist == null ||
      this._selectedItemIds.size === 0 ||
      this._deletingItems ||
      this._addingSelectedItems
    ) {
      return;
    }
    const count = this._selectedItemIds.size;
    const confirmed = await showDialog<boolean>(() => (
      <StickerConfirmDialog
        title="批量删除歌曲"
        body={`确定从“${playlist.name}”中删除已选择的 ${count} 首歌曲吗？`}
        confirmLabel="删除"
        confirmIcon={Icons.delete_outline}
        danger
      />
    ));
    if (!this.mounted || confirmed !== true) return;
    this.setState(() => {
      this._deletingItems = true;
      this._error = null;
    });
    try {
      await this.widget.controller.deleteItems({ playlistId: playlist.id, itemIds: [...this._selectedItemIds] });
      this.setState(() => {
        this._selectedItemIds = new Set();
        this._managingItems = false;
      });
      await this._loadItems({ reset: true });
      await this._reloadPlaylistSummariesWithoutClosing();
    } catch (error) {
      if (this.mounted) {
        this.setState(() => {
          this._error = String(error);
        });
      }
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._deletingItems = false;
        });
      }
    }
  };

  _addSelectedItemsToPlaylist = async (): Promise<void> => {
    const source = this._activePlaylist;
    if (source == null || !this.widget.controller.canBatchAddItems || this._selectedItemIds.size === 0 || this._addingSelectedItems) {
      return;
    }
    const targets = this._playlists.filter((playlist) => playlist.id !== source.id);
    if (targets.length === 0) {
      showFloatingErrorNotice('没有可添加歌曲的其他歌单');
      return;
    }
    const target = await showDialog<PersonalMusicPlaylist>(() => (
      <_MusicPlaylistImportDialog
        playlists={targets}
        selectedPlaylistId={null}
        title="选择目标歌单"
        emptyText="没有可添加歌曲的其他歌单"
        pickerKey="music-playlist-batch-add-target-picker"
        optionKeyPrefix="music-playlist-batch-add-target"
      />
    ));
    if (!this.mounted || target == null) return;

    const selectedIDs = [...this._selectedItemIds];
    const maxPlaylistItems = this._maxPlaylistItems;
    const available = Math.min(Math.max(maxPlaylistItems - target.itemCount, 0), maxPlaylistItems);
    if (available === 0) {
      showFloatingErrorNotice(`添加失败：“${target.name}”已达 ${maxPlaylistItems} 首上限`);
      return;
    }
    const exceedsLimit = selectedIDs.length > available;
    const confirmed = await showDialog<boolean>(() => (
      <StickerConfirmDialog
        title={exceedsLimit ? '歌曲不能全部添加' : '确认添加歌曲'}
        body={
          exceedsLimit
            ? `“${target.name}”最多还能添加 ${available} 首，无法全部添加。` +
              '系统会按选择顺序、按具体链接去重，最多添加到 ' +
              `${maxPlaylistItems} 首；原歌单中的歌曲不会删除。是否继续？`
            : `确定将已选择的 ${selectedIDs.length} 首歌曲按选择顺序添加到` +
              `“${target.name}”吗？重复链接不会再次添加，原歌单中的歌曲不会删除。`
        }
        confirmLabel={exceedsLimit ? '继续添加' : '确认添加'}
        confirmIcon={Icons.playlist_add}
      />
    ));
    if (!this.mounted || confirmed !== true) return;

    this.setState(() => {
      this._addingSelectedItems = true;
      this._error = null;
    });
    try {
      const result = await this.widget.controller.batchAddItems({
        sourcePlaylistId: source.id,
        targetPlaylistId: target.id,
        itemIds: selectedIDs,
      });
      if (!this.mounted || result == null) return;
      await this._reloadPlaylistSummariesWithoutClosing();
      if (!this.mounted) return;
      this.setState(() => {
        this._managingItems = false;
        this._selectedItemIds = new Set();
      });
      this._showPlaylistBatchAddResultNotice(result);
    } catch (error) {
      if (this.mounted) showFloatingErrorNotice(`添加歌曲失败：${String(error)}`);
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._addingSelectedItems = false;
        });
      }
    }
  };

  _showPlaylistBatchAddResultNotice(result: PersonalMusicPlaylistBatchAddResult): void {
    _addPostFrameCallback(() => {
      if (!this.mounted) return;
      const targetName = result.playlist.name;
      if (result.addedItemCount === 0 && result.omittedCount > 0) {
        showFloatingErrorNotice(`“${targetName}”已达 ${this._maxPlaylistItems} 首上限，没有歌曲被添加`);
        return;
      }
      if (result.addedItemCount === 0) {
        showFloatingSuccessNotice(`“${targetName}”已包含所选歌曲，未重复添加`);
        return;
      }
      if (result.truncated) {
        showFloatingSuccessNotice(
          `已添加到“${targetName}”，新增 ${result.addedItemCount} 首；` +
            `目标歌单已达 ${this._maxPlaylistItems} 首上限，${result.omittedCount} 首未添加`,
        );
        return;
      }
      const skipped = result.duplicateCount + result.alreadyPresentCount;
      const skippedLabel = skipped === 0 ? '' : `，已跳过 ${skipped} 首重复歌曲`;
      showFloatingSuccessNotice(`已添加到“${targetName}”，新增 ${result.addedItemCount} 首${skippedLabel}`);
    });
  }

  _pinSelectedItems = async (): Promise<void> => {
    const playlist = this._activePlaylist;
    if (
      !this._capabilities.canReorderItems ||
      playlist == null ||
      this._selectedItemIds.size === 0 ||
      this._pinningItems ||
      this._filterActive ||
      personalPlaylistItemOrderWithSelectionPinnedToFront({
        items: this._items,
        selectedItemIds: this._selectedItemIds,
      }) == null
    ) {
      return;
    }
    const selectedIds = [...this._selectedItemIds];
    this.setState(() => {
      this._pinningItems = true;
      this._error = null;
    });
    try {
      await this.widget.controller.pinItems({ playlistId: playlist.id, selectedItemIds: selectedIds });
      await this._loadItems({ reset: true });
    } catch (error) {
      if (this.mounted) {
        this.setState(() => {
          this._error = String(error);
        });
      }
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._pinningItems = false;
        });
      }
    }
  };

  async _moveItem(item: PersonalMusicPlaylistItem, delta: number): Promise<void> {
    const playlist = this._activePlaylist;
    if (!this._capabilities.canReorderItems || playlist == null || this._filterActive || this._busyItemIds.has(item.id)) {
      return;
    }
    this.setState(() => {
      this._busyItemIds.add(item.id);
      this._error = null;
    });
    try {
      await this.widget.controller.moveItem({ playlistId: playlist.id, itemId: item.id, delta });
      await this._loadItems({ reset: true });
    } catch (error) {
      if (this.mounted) {
        this.setState(() => {
          this._error = String(error);
        });
      }
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._busyItemIds.delete(item.id);
        });
      }
    }
  }

  build(): ReactNode {
    if (!this.widget.controller.available) {
      return <SettingsList>{[<_SettingsEmptyState key="empty" text={this.widget.unavailableMessage} />]}</SettingsList>;
    }
    const playlist = this._activePlaylist;
    return playlist == null ? this._buildPlaylistList() : this._buildPlaylistManager();
  }

  _buildPlaylistList(): ReactNode {
    const title = this.widget.title ?? '歌单管理';
    const visiblePlaylists = filteredPersonalMusicPlaylists(this._playlists, {
      keyword: this._playlistFilterKeyword,
      countFilter: this._playlistCountFilter,
    });
    const selectionNumbers = personalPlaylistSelectionNumbers(this._selectedPlaylistIds);
    const allVisibleSelected = personalPlaylistAllVisibleSelected({
      selectedPlaylistIds: this._selectedPlaylistIds,
      visiblePlaylists,
    });
    const canPinSelection =
      personalPlaylistOrderWithSelectionPinnedToFront({
        playlists: this._playlists,
        selectedPlaylistIds: this._selectedPlaylistIds,
      }) != null;
    const managing = this._managingPlaylists;
    const managementBusy = this._playlistManagementBusy;
    const capabilities = this._capabilities;
    let body: ReactNode;
    if (this._loading && this._playlists.length === 0) {
      body = (
        <Center style={{ flex: '1 1 0', minHeight: 0 }}>
          <CircularProgressIndicator color={_cyan} />
        </Center>
      );
    } else if (this._playlists.length === 0) {
      body = <_TopAlignedEmptyState text="暂无歌单，点击上方按钮新建" />;
    } else if (visiblePlaylists.length === 0) {
      body = <_TopAlignedEmptyState text="没有符合筛选条件的歌单" />;
    } else {
      body = (
        <_MusicPlaylistSummaryList
          playlists={visiblePlaylists}
          managing={managing}
          selectionNumbers={selectionNumbers}
          busy={managing && managementBusy}
          filterActive={this._playlistFilterActive}
          onTap={this._openOrSelectPlaylist}
          onRename={(playlist) => unawaited(this._renamePlaylist(playlist))}
          onMove={(playlist, delta) => unawaited(this._movePlaylist(playlist, delta))}
          useCloneAction={this.widget.controller.roomScoped}
          cloningPlaylistId={this._cloningPlaylistId}
          canShare={this.widget.shareApi != null && !this.widget.controller.roomScoped}
          sharingPlaylistId={this._sharingPlaylistId}
          onShare={(playlist) => unawaited(this._sharePersonalPlaylist(playlist))}
          onClone={this.widget.controller.canCloneRoomPlaylistsToPersonal ? (playlist) => unawaited(this._cloneRoomPlaylist(playlist)) : null}
        />
      );
    }
    const actions: StickerActionGridEntry[] = [
      new StickerActionGridEntry({
        label: managing ? '删除' : '新建歌单',
        button: (
          <Button
            key={managing ? 'delete-selected-personal-music-playlists' : 'create-personal-music-playlist'}
            data-testid={managing ? 'delete-selected-personal-music-playlists' : 'create-personal-music-playlist'}
            onPressed={
              managing
                ? capabilities.canDeletePlaylists && this._selectedPlaylistIds.length > 0 && !managementBusy
                  ? () => unawaited(this._deleteSelectedPlaylists())
                  : null
                : capabilities.canCreatePlaylists && !managementBusy && this._playlists.length < this._maxPlaylists
                  ? () => unawaited(this._createPlaylist())
                  : null
            }
            loading={managing ? this._deletingPlaylists : this._creating}
            tone={managing ? ButtonTone.danger : ButtonTone.primary}
            icon={managing ? Icons.delete_outline : Icons.playlist_add}
            width={Infinity}
          >
            {managing ? '删除' : '新建歌单'}
          </Button>
        ),
      }),
      new StickerActionGridEntry({
        label: managing ? '取消管理' : '管理',
        button: (
          <Button
            data-testid="manage-personal-music-playlists"
            onPressed={managementBusy || !capabilities.canManagePlaylists ? null : this._togglePlaylistManageMode}
            selected={managing}
            tone={managing ? ButtonTone.primary : ButtonTone.neutral}
            icon={managing ? Icons.close : Icons.checklist_rtl}
            width={Infinity}
          >
            {managing ? '取消管理' : '管理'}
          </Button>
        ),
      }),
      new StickerActionGridEntry({
        label: '筛选',
        button: (
          <Button
            data-testid="filter-personal-music-playlists"
            onPressed={managementBusy ? null : () => unawaited(this._openPlaylistFilter())}
            selected={this._playlistFilterActive}
            icon={Icons.filter_alt_outlined}
            width={Infinity}
          >
            筛选
          </Button>
        ),
      }),
    ];
    if (managing && this.widget.controller.canMergePlaylists) {
      actions.push(
        new StickerActionGridEntry({
          label: '合并',
          button: (
            <Button
              data-testid="merge-selected-music-playlists"
              onPressed={!managementBusy && this._selectedPlaylistIds.length >= 2 ? () => unawaited(this._mergeSelectedPlaylists()) : null}
              loading={this._mergingPlaylists}
              icon={Icons.merge_type}
              width={Infinity}
            >
              合并
            </Button>
          ),
        }),
      );
    }
    if (managing) {
      actions.push(
        new StickerActionGridEntry({
          label: '置顶',
          button: (
            <Button
              data-testid="pin-selected-personal-music-playlists"
              onPressed={
                !managementBusy && canPinSelection && capabilities.canReorderPlaylists ? () => unawaited(this._pinSelectedPlaylists()) : null
              }
              loading={this._pinningPlaylists}
              icon={Icons.vertical_align_top}
              width={Infinity}
            >
              置顶
            </Button>
          ),
        }),
        new StickerActionGridEntry({
          label: allVisibleSelected ? '取消全选' : '全选',
          button: (
            <Button
              data-testid="select-visible-personal-music-playlists"
              onPressed={!managementBusy && visiblePlaylists.length > 0 ? () => this._toggleSelectVisiblePlaylists(visiblePlaylists) : null}
              selected={allVisibleSelected}
              icon={allVisibleSelected ? Icons.check_box : Icons.check_box_outline_blank}
              width={Infinity}
            >
              {allVisibleSelected ? '取消全选' : '全选'}
            </Button>
          ),
        }),
      );
    }
    const headerChildren: ReactNode[] = [<StickerActionGrid key="actions" actions={actions} />];
    if (this._error != null) headerChildren.push(<_MusicPlaylistErrorText key="error" message={this._error} />);
    return (
      <SettingsFixedHeaderCard
        title={title}
        spacing={10}
        trailing={
          <Text softWrap={false} style={{ color: _textMuted, fontSize: 12, fontWeight: 600 }}>
            {this._playlistFilterActive
              ? `筛选 ${visiblePlaylists.length} / ${this._playlists.length}`
              : `${this._playlists.length} / ${this._maxPlaylists}`}
          </Text>
        }
        headerChildren={headerChildren}
        body={body}
      />
    );
  }

  _buildPlaylistItemsScroller(opts: { selectionNumbers: ReadonlyMap<string, number> }): ReactNode {
    const { selectionNumbers } = opts;
    if (this._loadingItems && this._items.length === 0) {
      return (
        <Center style={{ flex: '1 1 0', minHeight: 0 }}>
          <CircularProgressIndicator color={_cyan} />
        </Center>
      );
    }
    if (this._items.length === 0) {
      return <_TopAlignedEmptyState text={this._filterActive ? '没有匹配的歌曲' : '歌单还是空的'} />;
    }

    const items = this._items;
    const capabilities = this._capabilities;
    const shareApi = this.widget.shareApi ?? null;
    return (
      <div
        data-testid="personal-music-playlist-items-scroll"
        style={{ flex: '1 1 0', minHeight: 0, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 10, boxSizing: 'border-box' }}
      >
        {items.map((item, index) => (
          <div
            key={item.id}
            data-testid={`personal-music-playlist-item-${item.id}`}
            style={{ flexShrink: 0, display: 'flex', flexDirection: 'column', minWidth: 0 }}
          >
            <_MusicPlaylistItemTile
              item={item}
              managing={this._managingItems}
              selectionNumber={selectionNumbers.get(item.id) ?? null}
              busy={
                this._pinningItems ||
                this._deletingItems ||
                this._addingSelectedItems ||
                this._sharingItemId === item.id ||
                this._busyItemIds.has(item.id)
              }
              canMoveUp={!this._filterActive && index > 0}
              canMoveDown={!this._filterActive && (index < items.length - 1 || this._itemsHaveMore)}
              onTap={() => this._toggleItemSelection(item.id)}
              onShare={shareApi == null ? null : () => unawaited(this._sharePlaylistItem(item))}
              onMoveUp={() => unawaited(this._moveItem(item, -1))}
              onMoveDown={() => unawaited(this._moveItem(item, 1))}
              previewController={this._previewController}
              loadPlaylistTargets={this._loadTrackPlaylistTargets}
              onAddToPlaylistTarget={capabilities.canAddItems ? (target) => this._addPlaylistItemToPlaylistTarget(item, target) : null}
            />
          </div>
        ))}
        {this._itemsHaveMore ? (
          <div style={{ flexShrink: 0, display: 'flex', flexDirection: 'column' }}>
            <Button
              data-testid="load-more-personal-playlist-items"
              onPressed={this._loadingMore ? null : () => unawaited(this._loadItems({ reset: false }))}
              loading={this._loadingMore}
              icon={Icons.expand_more}
              width={Infinity}
            >
              加载更多
            </Button>
          </div>
        ) : null}
      </div>
    );
  }

  _buildPlaylistManager(): ReactNode {
    const playlist = this._activePlaylist!;
    const itemSelectionNumbers = personalPlaylistSelectionNumbers([...this._selectedItemIds]);
    const allVisibleSelected = this._items.length > 0 && this._items.every((item) => this._selectedItemIds.has(item.id));
    const canPinSelectedItems =
      !this._filterActive &&
      personalPlaylistItemOrderWithSelectionPinnedToFront({
        items: this._items,
        selectedItemIds: this._selectedItemIds,
      }) != null;
    const managing = this._managingItems;
    const busy = this._busy;
    const capabilities = this._capabilities;
    const actions: StickerActionGridEntry[] = [
      new StickerActionGridEntry({
        label: managing ? '删除' : '搜索添加',
        button: (
          <Button
            key={managing ? 'delete-selected-personal-music-playlist-items' : 'search-add-personal-music-playlist-item'}
            data-testid={managing ? 'delete-selected-personal-music-playlist-items' : 'search-add-personal-music-playlist-item'}
            onPressed={
              managing
                ? capabilities.canDeleteItems && !this._deletingItems && !this._addingSelectedItems && this._selectedItemIds.size > 0
                  ? () => unawaited(this._deleteSelectedItems())
                  : null
                : busy || !capabilities.canAddItems
                  ? null
                  : () => unawaited(this._openTrackSearchDialog())
            }
            loading={managing ? this._deletingItems : false}
            tone={managing ? ButtonTone.danger : ButtonTone.primary}
            icon={managing ? Icons.delete_outline : Icons.playlist_add}
            width={Infinity}
          >
            {managing ? '删除' : '搜索添加'}
          </Button>
        ),
      }),
      new StickerActionGridEntry({
        label: managing ? '取消管理' : '管理',
        button: (
          <Button
            data-testid="manage-personal-music-playlist-items"
            onPressed={busy || !capabilities.canManageItems ? null : this._toggleManagingItems}
            selected={managing}
            tone={managing ? ButtonTone.primary : ButtonTone.neutral}
            icon={managing ? Icons.close : Icons.checklist_rtl}
            width={Infinity}
          >
            {managing ? '取消管理' : '管理'}
          </Button>
        ),
      }),
      new StickerActionGridEntry({
        label: '筛选',
        button: (
          <Button
            data-testid="filter-personal-music-playlist-items"
            onPressed={busy ? null : () => unawaited(this._openItemFilterDialog())}
            selected={this._filterActive}
            icon={Icons.filter_alt_outlined}
            width={Infinity}
          >
            筛选
          </Button>
        ),
      }),
    ];
    if (managing) {
      actions.push(
        new StickerActionGridEntry({
          label: '添加到歌单',
          button: (
            <Button
              data-testid="add-selected-personal-music-playlist-items"
              onPressed={
                this.widget.controller.canBatchAddItems && !this._addingSelectedItems && this._selectedItemIds.size > 0
                  ? () => unawaited(this._addSelectedItemsToPlaylist())
                  : null
              }
              loading={this._addingSelectedItems}
              icon={Icons.playlist_add}
              width={Infinity}
            >
              添加到歌单
            </Button>
          ),
        }),
        new StickerActionGridEntry({
          label: '置顶',
          button: (
            <Button
              data-testid="pin-selected-personal-music-playlist-items"
              onPressed={!this._pinningItems && canPinSelectedItems && capabilities.canReorderItems ? () => unawaited(this._pinSelectedItems()) : null}
              loading={this._pinningItems}
              icon={Icons.vertical_align_top}
              width={Infinity}
            >
              置顶
            </Button>
          ),
        }),
        new StickerActionGridEntry({
          label: allVisibleSelected ? '取消全选' : '全选已加载',
          button: (
            <Button
              onPressed={this._items.length === 0 ? null : this._toggleSelectVisible}
              selected={allVisibleSelected}
              icon={allVisibleSelected ? Icons.check_box : Icons.check_box_outline_blank}
              width={Infinity}
            >
              {allVisibleSelected ? '取消全选' : '全选已加载'}
            </Button>
          ),
        }),
      );
    }
    const headerChildren: ReactNode[] = [<StickerActionGrid key="actions" actions={actions} />];
    if (this._filterActive) {
      headerChildren.push(
        <Text key="filter-note" style={{ color: _textMuted, fontSize: 12 }}>
          筛选状态下暂不提供排序，清除筛选后可调整顺序。
        </Text>,
      );
    }
    if (this._error != null) headerChildren.push(<_MusicPlaylistErrorText key="error" message={this._error} />);
    return (
      <SettingsFixedHeaderCard
        title={playlist.name}
        titleWidget={
          <Row data-testid="personal-music-playlist-header">
            <span data-testid="back-to-personal-music-playlists" style={{ display: 'contents' }}>
              <ButtonIconPlain tooltip="返回歌单列表" onPressed={busy ? null : this._closePlaylist} icon={Icons.arrow_back_rounded} />
            </span>
            <SizedBox width={10} />
            <Icon icon={Icons.queue_music_outlined} color={_cyan} size={20} />
            <SizedBox width={8} />
            <Expanded>
              <Text maxLines={1} overflow="ellipsis" style={{ color: _textPrimary, fontSize: 15, fontWeight: 600 }}>
                {playlist.name}
              </Text>
            </Expanded>
          </Row>
        }
        spacing={10}
        trailing={
          <Text softWrap={false} style={{ color: _textMuted, fontSize: 12, fontWeight: 600 }}>
            {this._filterActive
              ? `筛选 ${this._itemTotal} / ${playlist.itemCount} 首`
              : `${playlist.itemCount} / ${this._maxPlaylistItems} 首`}
          </Text>
        }
        headerChildren={headerChildren}
        body={this._buildPlaylistItemsScroller({ selectionNumbers: itemSelectionNumbers })}
      />
    );
  }
}

/** `Align(alignment: Alignment.topCenter, child: SizedBox(width: double.infinity, child: _SettingsEmptyState(...)))`. */
function _TopAlignedEmptyState({ text }: { text: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'stretch', width: '100%' }}>
      <_SettingsEmptyState text={text} />
    </div>
  );
}

export function MusicPlaylistsPanel(props: MusicPlaylistsPanelProps) {
  const state = useViewState(() => new _MusicPlaylistsPanelState(), props);
  return <>{state.build()}</>;
}
