import {
  applyMusicBoxProgress,
  applyMusicBoxRealtimeSnapshot,
  shouldAcceptMusicBoxSnapshot,
} from '@gang-chat/core/app/music_box_controller';
import * as music_box_display from '@gang-chat/core/app/music_box_display';
import { Timer, scheduleMicrotask, unawaited, withTimeout } from '@gang-chat/core/lib/async';
import { asString, type JsonMap } from '@gang-chat/core/lib/json';
import { ApiException } from '@gang-chat/core/protocol/api_client';
import {
  MusicBoxProgressEvent,
  MusicBoxState,
  type MusicBoxPlaybackMode,
  type MusicBoxQueueItem,
  type MusicBoxSearchResult,
} from '@gang-chat/core/protocol/models';

import { FloatingNoticeTone, showFloatingNotice } from '../ui/feedback';
import type { HomeShellState } from './home_shell';

const _musicBoxLoadRetryDelays: readonly number[] = [2_000, 5_000, 15_000, 30_000, 60_000];

/**
 * Music box orchestration: loading the per-room snapshot, search/queue/control
 * writes, the local progress ticker, and SSE-driven refreshes. Audio itself is
 * not handled here — once the user has joined the room's LiveKit session the
 * music box bot's track arrives as an ordinary remote audio track.
 */
export const HomeShellMusicBox = {
  _markMusicPlaylistsChanged(this: HomeShellState): void {
    if (!this.mounted) return;
    this._setHomeState(() => {
      this._musicPlaylistsRevision += 1;
    });
  },

  /**
   * Resets all music box state. Called on room switch and account change so a
   * stale snapshot never bleeds across rooms.
   */
  _resetMusicBox(this: HomeShellState): void {
    this._musicBoxLoadRetry?.cancel();
    this._musicBoxLoadRetry = null;
    this._musicBoxLoadFailures = 0;
    this._musicBoxSearchDebounce?.cancel();
    this._musicBoxSearchDebounce = null;
    this._musicBoxSearchSerial++;
    this._musicBoxRoomSerial++;
    this._musicBoxLoadingRoomId = null;
    this._musicBoxPendingRefreshRoomId = null;
    this._musicBox = null;
    this._musicBoxOpen = false;
    this._musicBoxSearchResults = [];
    this._musicBoxSearching = false;
    this._musicBoxSearchError = null;
    this._musicBoxSource = music_box_display.musicBoxDefaultSource;
    if (this._musicBoxSearchController.text.length > 0) {
      this._musicBoxSearchController.clear();
    }
    this._lastMusicBoxSearchText = this._musicBoxSearchController.text;
  },

  /**
   * Fetches the snapshot for [roomId]. A successful disabled snapshot still
   * hides the optional entry. Transient failures preserve the last valid
   * snapshot; clearing it would make both the inline player and open panel
   * disappear during token refreshes or brief network interruptions.
   */
  async _loadMusicBox(this: HomeShellState, roomId: string, opts: { refreshAfterCurrent?: boolean } = {}): Promise<void> {
    const { refreshAfterCurrent = false } = opts;
    if (this._musicBoxLoadingRoomId === roomId) {
      if (refreshAfterCurrent) {
        this._musicBoxPendingRefreshRoomId = roomId;
      }
      return;
    }
    const roomSerial = this._musicBoxRoomSerial;
    this._musicBoxLoadRetry?.cancel();
    this._musicBoxLoadRetry = null;
    this._musicBoxLoadingRoomId = roomId;
    try {
      const state = await withTimeout(this._musicBoxController.getState(roomId), 12_000);
      if (!this.mounted || this._selectedServerId !== roomId || roomSerial !== this._musicBoxRoomSerial) {
        return;
      }
      this._musicBoxLoadFailures = 0;
      this._setHomeState(() => {
        this._musicBox = state;
      });
    } catch (error) {
      // PORT: Dart logged this inside an `assert` (debug builds only).
      console.debug(`music-box: failed to load state for ${roomId}: ${String(error)}`);
      // Keep the last authoritative snapshot. When this was the first load,
      // schedule a bounded retry so one stalled request cannot hide the module
      // for the rest of the room session.
      this._scheduleMusicBoxLoadRetry(roomId, roomSerial);
    } finally {
      let runPendingRefresh = false;
      if (roomSerial === this._musicBoxRoomSerial && this._musicBoxLoadingRoomId === roomId) {
        this._musicBoxLoadingRoomId = null;
        runPendingRefresh = this._musicBoxPendingRefreshRoomId === roomId;
        if (runPendingRefresh) {
          this._musicBoxPendingRefreshRoomId = null;
        }
      }
      if (runPendingRefresh && this.mounted && this._selectedServerId === roomId && roomSerial === this._musicBoxRoomSerial) {
        scheduleMicrotask(() => unawaited(this._loadMusicBox(roomId, { refreshAfterCurrent: true })));
      }
    }
  },

  _scheduleMusicBoxLoadRetry(this: HomeShellState, roomId: string, roomSerial: number): void {
    if (
      !this.mounted ||
      this._selectedServerId !== roomId ||
      roomSerial !== this._musicBoxRoomSerial ||
      (this._musicBoxLoadRetry?.isActive ?? false)
    ) {
      return;
    }
    const delay =
      _musicBoxLoadRetryDelays[Math.min(Math.max(this._musicBoxLoadFailures, 0), _musicBoxLoadRetryDelays.length - 1)]!;
    this._musicBoxLoadFailures++;
    this._musicBoxLoadRetry = new Timer(delay, () => {
      this._musicBoxLoadRetry = null;
      if (!this.mounted || this._selectedServerId !== roomId || roomSerial !== this._musicBoxRoomSerial) {
        return;
      }
      unawaited(this._loadMusicBox(roomId));
    });
  },

  _ensureMusicBoxLoaded(this: HomeShellState, roomId: string): void {
    if (this._selectedServerId !== roomId || this._musicBox != null) return;
    unawaited(this._loadMusicBox(roomId));
  },

  _toggleMusicBoxPanel(this: HomeShellState): void {
    this._setHomeState(() => {
      this._musicBoxOpen = !this._musicBoxOpen;
    });
  },

  /** Switches the search source and re-runs the current query against it. */
  _changeMusicBoxSource(this: HomeShellState, source: string): void {
    const normalizedSource = music_box_display.normalizedMusicBoxSource(source);
    if (normalizedSource === this._musicBoxSource) return;
    this._setHomeState(() => {
      this._musicBoxSource = normalizedSource;
    });
    const keyword = this._musicBoxSearchController.text.trim();
    if (keyword.length === 0) return;
    this._musicBoxSearchDebounce?.cancel();
    this._setHomeState(() => {
      this._musicBoxSearchResults = [];
      this._musicBoxSearching = true;
      this._musicBoxSearchError = null;
    });
    unawaited(this._searchMusicBox(keyword));
  },

  /**
   * Applies an authoritative snapshot from a write response or SSE event,
   * overwriting local state wholesale per the server contract.
   */
  _applyMusicBoxSnapshot(this: HomeShellState, state: MusicBoxState): void {
    if (!this.mounted) return;
    if (!shouldAcceptMusicBoxSnapshot(this._musicBox, state)) return;
    this._setHomeState(() => {
      this._musicBox = state;
    });
  },

  _onMusicBoxChanged(this: HomeShellState, event: JsonMap): void {
    const roomId = asString(event['room_id']);
    if (roomId == null || roomId !== this._selectedServerId) return;
    // The realtime client flattens the event envelope, merging the payload's
    // fields up alongside `room_id` (see LiveStreamClient._emit). The snapshot
    // therefore lives at the top level of [event], not under a `data` key.
    const incoming = MusicBoxState.fromJson(event);
    const current = this._musicBox;
    const next = applyMusicBoxRealtimeSnapshot(current, incoming);
    if (next === current) return;
    if (next != null) {
      this._setHomeState(() => {
        this._musicBox = next;
      });
    }
    // A same-revision compatibility heartbeat was reduced to a position-only
    // update above, preserving actor-specific capabilities and avoiding a GET
    // on every second. Only an accepted structural/legacy snapshot needs the
    // personalized refresh.
    if (next !== incoming) return;
    // Room fan-out cannot carry one shared capability set because every
    // subscriber may have a different room role. Refresh the actor-specific
    // snapshot after applying the structural event; concurrent events are
    // coalesced by _loadMusicBox.
    unawaited(this._loadMusicBox(roomId, { refreshAfterCurrent: true }));
  },

  _onMusicBoxProgress(this: HomeShellState, event: JsonMap): void {
    const roomId = asString(event['room_id']);
    if (roomId == null || roomId !== this._selectedServerId) return;
    const next = applyMusicBoxProgress(this._musicBox, MusicBoxProgressEvent.fromJson(event));
    if (next == null || next === this._musicBox) return;
    this._setHomeState(() => {
      this._musicBox = next;
    });
  },

  // --- Writes -----------------------------------------------------------

  async _searchMusicBox(this: HomeShellState, keyword: string): Promise<void> {
    const roomId = this._selectedServerId;
    const trimmed = keyword.trim();
    if (roomId == null) return;
    if (trimmed.length === 0) {
      this._setHomeState(() => {
        this._musicBoxSearchResults = [];
        this._musicBoxSearching = false;
        this._musicBoxSearchError = null;
      });
      return;
    }
    const serial = ++this._musicBoxSearchSerial;
    this._setHomeState(() => {
      this._musicBoxSearching = true;
      this._musicBoxSearchError = null;
    });
    try {
      const results = await this._musicBoxController.search({
        roomId,
        keyword: trimmed,
        source: music_box_display.normalizedMusicBoxSource(this._musicBoxSource),
      });
      if (!this.mounted || serial !== this._musicBoxSearchSerial) return;
      this._setHomeState(() => {
        this._musicBoxSearchResults = results;
        this._musicBoxSearching = false;
      });
    } catch (error) {
      if (!this.mounted || serial !== this._musicBoxSearchSerial) return;
      this._setHomeState(() => {
        this._musicBoxSearching = false;
        this._musicBoxSearchError = this._musicBoxSearchErrorMessage(error);
      });
    }
  },

  /**
   * Debounced search driven by the search field's controller listener.
   *
   * Flips the searching state immediately so the panel swaps to the results
   * view (and shows a spinner) on the first keystroke, then debounces the
   * actual network call.
   */
  _handleMusicBoxSearchChanged(this: HomeShellState): void {
    const rawKeyword = this._musicBoxSearchController.text;
    if (rawKeyword === this._lastMusicBoxSearchText) return;
    this._lastMusicBoxSearchText = rawKeyword;
    this._musicBoxSearchDebounce?.cancel();
    const keyword = rawKeyword.trim();
    if (keyword.length === 0) {
      unawaited(this._searchMusicBox(''));
      return;
    }
    this._setHomeState(() => {
      this._musicBoxSearching = true;
      this._musicBoxSearchError = null;
    });
    this._musicBoxSearchDebounce = new Timer(350, () => unawaited(this._searchMusicBox(keyword)));
  },

  async _queueMusicBoxTrack(this: HomeShellState, result: MusicBoxSearchResult): Promise<void> {
    const roomId = this._selectedServerId;
    if (roomId == null) return;
    try {
      const state = await this._musicBoxController.queueSearchResult({
        roomId,
        result,
      });
      this._applyMusicBoxSnapshot(state);
      this._showMusicBoxNotice(`已加入队列：${result.name}`, { tone: FloatingNoticeTone.success });
    } catch (error) {
      this._showMusicBoxNotice(this._musicBoxWriteErrorMessage(error), { tone: FloatingNoticeTone.error });
    }
  },

  async _removeMusicBoxItem(this: HomeShellState, item: MusicBoxQueueItem): Promise<void> {
    const roomId = this._selectedServerId;
    if (roomId == null) return;
    try {
      const state = await this._musicBoxController.removeItem({
        roomId,
        itemId: item.id,
      });
      this._applyMusicBoxSnapshot(state);
      this._showMusicBoxNotice(`已从队列删除：${item.title}`, { tone: FloatingNoticeTone.success });
    } catch (error) {
      this._showMusicBoxNotice(this._musicBoxWriteErrorMessage(error), { tone: FloatingNoticeTone.error });
    }
  },

  async _controlMusicBox(
    this: HomeShellState,
    action: string,
    opts: { mode?: MusicBoxPlaybackMode | null } = {},
  ): Promise<void> {
    const roomId = this._selectedServerId;
    if (roomId == null) return;
    try {
      const state = await this._musicBoxController.control({
        roomId,
        action,
        mode: opts.mode ?? null,
        currentState: this._musicBox,
      });
      this._applyMusicBoxSnapshot(state);
    } catch (error) {
      this._showMusicBoxNotice(this._musicBoxWriteErrorMessage(error), { tone: FloatingNoticeTone.error });
    }
  },

  _changeMusicBoxPlaybackMode(this: HomeShellState, mode: MusicBoxPlaybackMode): Promise<void> {
    return this._controlMusicBox('set_mode', { mode });
  },

  _toggleMusicBoxPlayback(this: HomeShellState): void {
    const state = this._musicBox;
    if (state == null) return;
    const action = music_box_display.musicBoxPrimaryTransport(state);
    unawaited(this._controlMusicBox(music_box_display.musicBoxTransportApiAction(action)));
  },

  // --- Error mapping ----------------------------------------------------

  _musicBoxSearchErrorMessage(this: HomeShellState, error: unknown): string {
    if (error instanceof ApiException && error.statusCode === 502) {
      return '搜索服务暂时不可用，请稍后重试';
    }
    return '搜索失败，请重试';
  },

  _musicBoxWriteErrorMessage(this: HomeShellState, error: unknown): string {
    if (error instanceof ApiException) {
      switch (error.code) {
        case 'music_box_unavailable':
          return '音乐盒当前不可用';
        case 'music_box_item_already_queued':
          return '已在队列中';
        case 'music_box_queue_limit_reached':
          return '点歌队列已达 200 首上限';
      }
    }
    return '操作失败，请重试';
  },

  _showMusicBoxNotice(this: HomeShellState, message: string, opts: { tone?: FloatingNoticeTone } = {}): void {
    const { tone = FloatingNoticeTone.info } = opts;
    if (!this.mounted) return;
    showFloatingNotice(message, { tone });
  },
};
