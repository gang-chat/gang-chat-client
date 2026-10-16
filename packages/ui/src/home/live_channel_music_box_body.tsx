import { createRef, type ReactNode, type RefObject } from 'react';

import { unawaited } from '@gang-chat/core/lib/async';
import * as music_box_display from '@gang-chat/core/app/music_box_display';
import { musicBoxControlErrorMessage, type MusicBoxController } from '@gang-chat/core/app/music_box_controller';
import {
  MusicBoxActiveSourceType,
  MusicBoxPlaybackState,
  MusicBoxQueueItemStatus,
  PersonalMusicPlaylist,
  musicBoxActiveSourceTypeValue,
  type CurrentUser,
  type MusicBoxQueueItem,
  type MusicBoxSearchResult,
  type MusicBoxState,
  type PersonalMusicPlaylistItem,
  type PublicRoom,
} from '@gang-chat/core/protocol/models';

import { Center, Expanded, Row, SizedBox, Spacer, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { FocusNode, type TextEditingController } from '../lib/text_editing';
import { ViewState, useViewState } from '../lib/view_state';
import { ButtonTone } from '../ui/button';
import { DialogFrame, ResponsiveDialogAction, showFloatingErrorNotice, showFloatingSuccessNotice } from '../ui/feedback';
import { Icon, Icons, type IconData } from '../ui/icon';
import { Input } from '../ui/input';
import { showDialog } from '../ui/overlay';
import { Segment, SegmentedControl } from '../ui/segmented_control';
import { UiColors, UiCurves, UiMotion, UiRadii, UiTypography, kClientFontStack } from '../ui/tokens';
import { _musicBoxControlHeight } from './live_channel_music_box';
import {
  _MusicBoxEmpty,
  _MusicBoxLoading,
  _MusicBoxPlaylistRow,
  _MusicBoxQueueRowLeading,
  _MusicBoxRequesterAvatar,
  _MusicBoxRetryState,
  _MusicBoxRowAction,
  _MusicBoxSectionHeader,
  _MusicBoxTrackRow,
  _musicBoxPlaylistItemAsResult,
  _musicBoxRowMinHeight,
} from './live_channel_music_box_rows';
import { _MusicBoxSongCard, _musicBoxActiveSourceOwner } from './live_channel_music_box_song_card';
import {
  MusicPlaylistCardData,
  MusicPlaylistCardHostScope,
  MusicPlaylistHoverCard,
  type MusicPlaylistEditCallback,
} from './music_playlist_profile_card';
import type { RoomProfileResolver, UserProfileActionBuilder, UserProfileResolver } from './room_profile_card';

type _ValueChanged<T> = (value: T) => void;

// ---------------------------------------------------------------------------
// Scrolling helpers (Dart `ScrollController` / `Scrollable.ensureVisible`).

/** Dart `WidgetsBinding.instance.addPostFrameCallback`. */
function _addPostFrameCallback(callback: () => void): void {
  if (typeof requestAnimationFrame === 'function') {
    requestAnimationFrame(() => callback());
  } else {
    setTimeout(callback, 0);
  }
}

/** Animates [scroller]'s offset to [to] over [duration] ms with easeOutCubic. */
function _musicBoxAnimateScroll(scroller: HTMLElement, to: number, duration: number): () => void {
  const from = scroller.scrollTop;
  if (duration <= 0 || Math.abs(to - from) < 0.5 || typeof requestAnimationFrame !== 'function') {
    scroller.scrollTop = to;
    return () => {};
  }
  const start = performance.now();
  let cancelled = false;
  let frame = 0;
  const tick = (now: number) => {
    if (cancelled) return;
    const t = Math.min((now - start) / duration, 1);
    const eased = 1 - Math.pow(1 - t, 3);
    scroller.scrollTop = from + (to - from) * eased;
    if (t < 1) frame = requestAnimationFrame(tick);
  };
  frame = requestAnimationFrame(tick);
  return () => {
    cancelled = true;
    cancelAnimationFrame(frame);
  };
}

/**
 * Port of `Scrollable.ensureVisible(context, alignment:, duration:)` for the
 * music-box list: scrolls [scroller] so [target] sits at [alignment] of the
 * viewport (0 = top, 0.5 = centred), clamped to the scroll extent.
 */
function _musicBoxEnsureVisible(
  scroller: HTMLElement,
  target: HTMLElement,
  opts: { alignment: number; duration?: number },
): () => void {
  const { alignment, duration = 0 } = opts;
  const scrollerRect = scroller.getBoundingClientRect();
  const targetRect = target.getBoundingClientRect();
  const offset = targetRect.top - scrollerRect.top - scroller.clientTop + scroller.scrollTop;
  const maxScroll = Math.max(scroller.scrollHeight - scroller.clientHeight, 0);
  const destination = Math.min(Math.max(offset - (scroller.clientHeight - targetRect.height) * alignment, 0), maxScroll);
  return _musicBoxAnimateScroll(scroller, destination, duration);
}

// ---------------------------------------------------------------------------

export interface _MusicBoxBodyProps {
  state: MusicBoxState;
  searchOpen: boolean;
  openSections: ReadonlySet<music_box_display.MusicBoxSection>;
  openPlaylistKey: string | null;
  revealSerial: number;
  revealSection: music_box_display.MusicBoxSection | null;
  onToggleSearch: () => void;
  onToggleSection: _ValueChanged<music_box_display.MusicBoxSection>;
  onTogglePlaylist: (type: MusicBoxActiveSourceType, id: string) => void;
  onOpenPlaylist: (type: MusicBoxActiveSourceType, id: string) => void;
  searchController: TextEditingController;
  searchResults: readonly MusicBoxSearchResult[];
  searching: boolean;
  searchError: string | null;
  source: string;
  onQueueResult: _ValueChanged<MusicBoxSearchResult>;
  onRemoveItem: _ValueChanged<MusicBoxQueueItem>;
  onSourceChanged: _ValueChanged<string>;
  controller: MusicBoxController | null;
  roomId: string | null;
  playlistsRevision: number;
  room: PublicRoom | null;
  onStateChanged: _ValueChanged<MusicBoxState> | null;
  currentUser: CurrentUser | null;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: _ValueChanged<PublicRoom> | null;
  userProfileActionBuilder: UserProfileActionBuilder | null;
  onCreateFirstRoomPlaylist: (() => void) | null;
  onCreateFirstPersonalPlaylist: (() => void) | null;
  onEditRoomPlaylist: MusicPlaylistEditCallback | null;
  onEditPersonalPlaylist: MusicPlaylistEditCallback | null;
}

/**
 * Lower body of the music box: one flat scrolling list. The request queue
 * sits on top and is always open; a search button on its header unfolds a
 * search panel right under it. Below come 房间歌单 and 我的歌单 as collapsed
 * groups; opening a group lists its playlists, opening a playlist lists its
 * tracks in place. Nothing morphs and nothing navigates away.
 */
export class _MusicBoxBodyState extends ViewState<_MusicBoxBodyProps> {
  /** Dart `ScrollController`: the scrolling element. */
  readonly _scrollController = createRef<HTMLDivElement>();
  /** Dart `GlobalKey`s of the section headers. */
  readonly _sectionKeys = new Map<music_box_display.MusicBoxSection, RefObject<HTMLDivElement | null>>(
    Object.values(music_box_display.MusicBoxSection).map((section) => [section, createRef<HTMLDivElement>()]),
  );
  _cancelReveal: (() => void) | null = null;

  override initState(): void {
    this.widget.searchController.addListener(this._handleSearchChanged);
  }

  override didUpdateProps(oldWidget: _MusicBoxBodyProps): void {
    if (oldWidget.searchController !== this.widget.searchController) {
      oldWidget.searchController.removeListener(this._handleSearchChanged);
      this.widget.searchController.addListener(this._handleSearchChanged);
    }
    if (oldWidget.revealSerial !== this.widget.revealSerial) {
      const section = this.widget.revealSection;
      if (section != null) this._scheduleReveal(section);
    }
  }

  override dispose(): void {
    this.widget.searchController.removeListener(this._handleSearchChanged);
    this._cancelReveal?.();
  }

  _handleSearchChanged = (): void => {
    if (this.mounted) this.setState();
  };

  _scheduleReveal(section: music_box_display.MusicBoxSection): void {
    _addPostFrameCallback(() => {
      if (!this.mounted) return;
      const header = this._sectionKeys.get(section)?.current ?? null;
      const scroller = this._scrollController.current;
      if (header == null || scroller == null) return;
      this._cancelReveal?.();
      this._cancelReveal = _musicBoxEnsureVisible(scroller, header, { alignment: 0, duration: 180 });
    });
  }

  _viewPlaylist = async (playlist: PersonalMusicPlaylist, roomScoped: boolean): Promise<void> => {
    // Dart `FocusManager.instance.primaryFocus?.unfocus()`.
    if (typeof document !== 'undefined' && document.activeElement instanceof HTMLElement) {
      document.activeElement.blur();
    }
    this.widget.onOpenPlaylist(roomScoped ? MusicBoxActiveSourceType.roomPlaylist : MusicBoxActiveSourceType.userPlaylist, playlist.id);
  };

  _playlistGroup(type: MusicBoxActiveSourceType): ReactNode {
    const widget = this.widget;
    const section = music_box_display.musicBoxSectionForSource(type);
    const roomScoped = type === MusicBoxActiveSourceType.roomPlaylist;
    return (
      <_MusicBoxPlaylistGroup
        key={`music-box-group:${musicBoxActiveSourceTypeValue(type)}`}
        headerKey={this._sectionKeys.get(section)!}
        type={type}
        open={widget.openSections.has(section)}
        openPlaylistKey={widget.openPlaylistKey}
        state={widget.state}
        controller={widget.controller}
        roomId={widget.roomId}
        playlistsRevision={widget.playlistsRevision}
        room={widget.room}
        currentUser={widget.currentUser}
        onStateChanged={widget.onStateChanged}
        onToggle={() => widget.onToggleSection(section)}
        onTogglePlaylist={(id) => widget.onTogglePlaylist(type, id)}
        onCreateFirstPlaylist={roomScoped ? widget.onCreateFirstRoomPlaylist : widget.onCreateFirstPersonalPlaylist}
        onEdit={roomScoped ? widget.onEditRoomPlaylist : widget.onEditPersonalPlaylist}
        onResolveUserProfile={widget.onResolveUserProfile}
        onResolveRoomProfile={widget.onResolveRoomProfile}
        onEnterCommonRoom={widget.onEnterCommonRoom}
        userProfileActionBuilder={widget.userProfileActionBuilder}
      />
    );
  }
}

export function _MusicBoxBody(props: _MusicBoxBodyProps) {
  const state = useViewState(() => new _MusicBoxBodyState(), props);
  const widget = props;
  const query = widget.searchController.text;
  const hasQuery = widget.searchOpen && query.trim().length > 0;
  return (
    <MusicPlaylistCardHostScope
      currentUser={widget.currentUser}
      room={widget.room}
      onResolveUserProfile={widget.onResolveUserProfile}
      onResolveRoomProfile={widget.onResolveRoomProfile}
      onEnterCommonRoom={widget.onEnterCommonRoom}
      userProfileActionBuilder={widget.userProfileActionBuilder}
      onStateChanged={widget.onStateChanged}
      onViewPlaylist={state._viewPlaylist}
      onEditRoomPlaylist={widget.onEditRoomPlaylist ?? undefined}
      onEditPersonalPlaylist={widget.onEditPersonalPlaylist ?? undefined}
    >
      {/* Absolutely filled so the list keeps its bounds whatever wrapper the
          scope renders. */}
      <div
        ref={state._scrollController}
        data-testid="music-box-list"
        style={{ position: 'absolute', inset: 0, overflowX: 'hidden', overflowY: 'auto', boxSizing: 'border-box' }}
      >
        <_MusicBoxQueueSection
          headerKey={state._sectionKeys.get(music_box_display.MusicBoxSection.queue)!}
          scrollController={state._scrollController}
          state={widget.state}
          searchOpen={widget.searchOpen}
          query={query}
          searchController={widget.searchController}
          searchResults={widget.searchResults}
          searching={widget.searching}
          searchError={widget.searchError}
          source={widget.source}
          onToggleSearch={widget.onToggleSearch}
          onSourceChanged={widget.onSourceChanged}
          onQueueResult={widget.onQueueResult}
          onRemoveItem={widget.onRemoveItem}
          controller={widget.controller}
          roomId={widget.roomId}
          onStateChanged={widget.onStateChanged}
          currentUser={widget.currentUser}
          onResolveUserProfile={widget.onResolveUserProfile}
          onResolveRoomProfile={widget.onResolveRoomProfile}
          onEnterCommonRoom={widget.onEnterCommonRoom}
          userProfileActionBuilder={widget.userProfileActionBuilder}
        />
        {/* While searching, the results take the whole list so the user is
            not scrolling past playlists to find the next hit. */}
        {!hasQuery ? (
          <>
            {state._playlistGroup(MusicBoxActiveSourceType.roomPlaylist)}
            {state._playlistGroup(MusicBoxActiveSourceType.userPlaylist)}
          </>
        ) : null}
      </div>
    </MusicPlaylistCardHostScope>
  );
}

/**
 * What [_musicBoxRunWrite] needs from its caller: Dart's `context.mounted`.
 * PORT: replaces the `BuildContext` argument; ViewState instances satisfy it.
 */
export interface _MusicBoxWriteContext {
  readonly mounted: boolean;
}

/**
 * Runs a music-box write, applies the returned snapshot, and surfaces
 * failures as a floating notice.
 */
export async function _musicBoxRunWrite(
  context: _MusicBoxWriteContext,
  opts: {
    write: () => Promise<MusicBoxState>;
    onStateChanged: _ValueChanged<MusicBoxState> | null | undefined;
    failureMessage: string;
    successMessage?: string | null;
  },
): Promise<void> {
  const { write, onStateChanged, failureMessage, successMessage } = opts;
  try {
    const state = await write();
    onStateChanged?.(state);
    if (successMessage != null && context.mounted) {
      showFloatingSuccessNotice(successMessage);
    }
  } catch (error) {
    if (context.mounted) {
      showFloatingErrorNotice(musicBoxControlErrorMessage(error, failureMessage));
    }
  }
}

/**
 * A box-height slot for the shared empty/loading/error widgets, which center
 * themselves and therefore need bounded height inside the list.
 */
export function _musicBoxSliverBox(child: ReactNode, opts: { height?: number; key?: string } = {}): ReactNode {
  const { height = 120, key } = opts;
  return (
    <SizedBox key={key} height={height}>
      {child}
    </SizedBox>
  );
}

export interface _MusicBoxGroupHeaderProps {
  /** Two-digit channel number printed in front of the group title. */
  channel: string;
  title: string;
  icon: IconData;
  open: boolean;
  playing: boolean;
  onToggle: () => void;
  count?: number | null;
  /** Dart `key` (test id). */
  'data-testid'?: string;
}

/** Tappable header of a collapsed/expanded group in the flat list. */
export function _MusicBoxGroupHeader({ channel, title, icon, open, playing, onToggle, count, 'data-testid': testId }: _MusicBoxGroupHeaderProps) {
  return (
    <div
      role="button"
      aria-expanded={open}
      aria-label={title}
      data-testid={testId}
      onClick={(event) => {
        if (event.button !== 0) return;
        onToggle();
      }}
      // The Dart Container's top margin sits inside the opaque tap target.
      style={{ paddingTop: 4, cursor: SystemMouseCursors.click }}
    >
      <Row
        style={{
          height: 40,
          boxSizing: 'border-box',
          padding: '0 6px',
          borderTop: `1px solid ${UiColors.border}`,
        }}
      >
        <Text softWrap={false} style={{ ...UiTypography.dot, fontSize: 13, color: playing ? UiColors.accent : UiColors.textMuted }}>
          {channel}
        </Text>
        <SizedBox width={10} />
        <Icon icon={playing ? Icons.graphic_eq : icon} size={15} color={playing ? UiColors.accent : UiColors.textSecondary} />
        <SizedBox width={8} />
        <Text softWrap={false} style={{ ...UiTypography.label, color: playing ? UiColors.accent : UiColors.text, fontWeight: 700 }}>
          {title}
        </Text>
        {count != null ? (
          <>
            <SizedBox width={6} />
            <Text softWrap={false} style={{ ...UiTypography.mono, color: UiColors.textMuted, fontSize: 10.5 }}>
              {`${count}`}
            </Text>
          </>
        ) : null}
        <Spacer />
        {/* AnimatedRotation(turns: open ? 0.25 : 0, duration: fast). */}
        <div
          style={{
            display: 'flex',
            flexShrink: 0,
            transform: `rotate(${open ? 0.25 : 0}turn)`,
            transition: `transform ${UiMotion.fast}ms ${UiCurves.linear}`,
          }}
        >
          <Icon icon={Icons.chevron_right_rounded} size={18} color={UiColors.textMuted} />
        </div>
      </Row>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Request queue

export interface _MusicBoxQueueSectionProps {
  headerKey: RefObject<HTMLDivElement | null>;
  scrollController: RefObject<HTMLDivElement | null>;
  state: MusicBoxState;
  searchOpen: boolean;
  query: string;
  searchController: TextEditingController;
  searchResults: readonly MusicBoxSearchResult[];
  searching: boolean;
  searchError: string | null;
  source: string;
  onToggleSearch: () => void;
  onSourceChanged: _ValueChanged<string>;
  onQueueResult: _ValueChanged<MusicBoxSearchResult>;
  onRemoveItem: _ValueChanged<MusicBoxQueueItem>;
  controller: MusicBoxController | null;
  roomId: string | null;
  onStateChanged: _ValueChanged<MusicBoxState> | null;
  currentUser: CurrentUser | null;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: _ValueChanged<PublicRoom> | null;
  userProfileActionBuilder: UserProfileActionBuilder | null;
}

/**
 * The request queue: header with the search toggle and queue-wide actions,
 * the unfolded search panel, then either the search results or the rows.
 */
export class _MusicBoxQueueSectionState extends ViewState<_MusicBoxQueueSectionProps> {
  // 26px raised buttons paint 34px tall (lift + depth), plus the 4px gap.
  static readonly _headerHeight = 34 + 4;
  // A getter: `_musicBoxControlHeight` comes from a module in an import
  // cycle, so it must not be read while modules are still loading.
  static get _searchPanelHeight(): number {
    return _musicBoxControlHeight + 8;
  }

  /** Dart `GlobalKey` of the current row. */
  readonly _currentRowKey = createRef<HTMLDivElement>();
  readonly _searchFocus = new FocusNode();
  _revealedItemId: string | null = null;
  _playingNowItemId: string | null = null;

  get _isActive(): boolean {
    return music_box_display.musicBoxSourceIsActive(this.widget.state, MusicBoxActiveSourceType.temporary);
  }

  get _items(): readonly MusicBoxQueueItem[] {
    return this._isActive ? this.widget.state.queue : this.widget.state.temporaryQueue;
  }

  override didUpdateProps(oldWidget: _MusicBoxQueueSectionProps): void {
    if (!oldWidget.searchOpen && this.widget.searchOpen) {
      _addPostFrameCallback(() => {
        if (this.mounted) this._searchFocus.requestFocus();
      });
    }
  }

  override dispose(): void {
    this._searchFocus.dispose();
  }

  /**
   * Flutter built the rows lazily, so a current item deep in the queue had
   * no context yet: it jumped near it using the row minimum height as an
   * estimate, then let `Scrollable.ensureVisible` correct for taller rows on
   * the next frame. PORT: every DOM row exists after the commit, so both
   * steps run in the same post-frame callback.
   */
  _scheduleRevealCurrent(currentItemId: string, currentIndex: number): void {
    if (currentItemId.length === 0 || currentIndex < 0 || this._revealedItemId === currentItemId) {
      return;
    }
    this._revealedItemId = currentItemId;
    _addPostFrameCallback(() => {
      const scroller = this.widget.scrollController.current;
      if (!this.mounted || scroller == null) return;
      const maxScroll = Math.max(scroller.scrollHeight - scroller.clientHeight, 0);
      const estimatedCenter =
        _MusicBoxQueueSectionState._headerHeight +
        (this.widget.searchOpen ? _MusicBoxQueueSectionState._searchPanelHeight : 0) +
        currentIndex * _musicBoxRowMinHeight +
        _musicBoxRowMinHeight / 2;
      const target = estimatedCenter - scroller.clientHeight / 2;
      scroller.scrollTop = Math.min(Math.max(target, 0), maxScroll);
      const currentRow = this._currentRowKey.current;
      if (currentRow == null) return;
      _musicBoxEnsureVisible(scroller, currentRow, { alignment: 0.5, duration: 0 });
    });
  }

  /**
   * Plays [item]. While the request queue is the active source this is a
   * server `play_now`; while a saved playlist is playing, picking a queue
   * song switches the room back to the request queue starting from it.
   */
  async _playItem(item: MusicBoxQueueItem): Promise<void> {
    const controller = this.widget.controller;
    const roomId = this.widget.roomId;
    if (controller == null || roomId == null || this._playingNowItemId != null) {
      return;
    }
    const switching = !this._isActive;
    this.setState(() => {
      this._playingNowItemId = item.id;
    });
    await _musicBoxRunWrite(this, {
      write: () =>
        switching
          ? controller.activatePlaylist({
              roomId,
              sourceType: MusicBoxActiveSourceType.temporary,
              startItemId: item.id,
            })
          : controller.playNow({ roomId, item }),
      onStateChanged: this.widget.onStateChanged,
      failureMessage: switching ? '播放点歌队列失败，请重试' : '优先播放失败，请重试',
      successMessage: switching ? null : '已优先播放',
    });
    if (this.mounted) {
      this.setState(() => {
        this._playingNowItemId = null;
      });
    }
  }

  async _confirmAndClear(): Promise<void> {
    const controller = this.widget.controller;
    const roomId = this.widget.roomId;
    if (controller == null || roomId == null) return;
    const confirmed = await showDialog<boolean>((close) => (
      <DialogFrame
        title="清空点歌队列"
        icon={Icons.delete_sweep_outlined}
        adaptiveActions={[
          new ResponsiveDialogAction({ label: '取消', onPressed: () => close(false) }),
          new ResponsiveDialogAction({
            buttonKey: 'music-box-confirm-clear-temporary-queue',
            label: '清空队列',
            icon: Icons.delete_sweep_outlined,
            tone: ButtonTone.danger,
            onPressed: () => close(true),
          }),
        ]}
      >
        <Text style={UiTypography.body}>确认清空当前点歌队列？此操作不会删除已保存的歌单。</Text>
      </DialogFrame>
    ));
    if (confirmed !== true || !this.mounted) return;
    await _musicBoxRunWrite(this, {
      write: () => controller.clearTemporaryQueue({ roomId, currentState: this.widget.state }),
      onStateChanged: this.widget.onStateChanged,
      failureMessage: '清空点歌队列失败，请重试',
      successMessage: '已清空点歌队列',
    });
  }

  _headerTitle(count: number): string {
    let status: string;
    if (!this._isActive) {
      status = '未播放';
    } else {
      switch (this.widget.state.playback.state) {
        case MusicBoxPlaybackState.playing:
          status = '正在播放';
          break;
        case MusicBoxPlaybackState.paused:
          status = '已暂停';
          break;
        case MusicBoxPlaybackState.stopped:
          status = '未播放';
          break;
      }
    }
    return `点歌队列 · ${count} 首 · ${status}`;
  }

  _header(): ReactNode {
    const widget = this.widget;
    const state = widget.state;
    const items = this._items;
    const canWrite = widget.controller != null && widget.roomId != null;
    const lit = this._isActive && music_box_display.musicBoxHasActivePlayback(state);
    const trailing: ReactNode[] = [
      <_MusicBoxRowAction
        key="search"
        data-testid="music-box-search-toggle"
        icon={widget.searchOpen ? Icons.search_off : Icons.search}
        label={widget.searchOpen ? '收起搜索' : '搜索歌曲点歌'}
        tone={ButtonTone.primary}
        selected={widget.searchOpen}
        onPressed={widget.onToggleSearch}
        size={26}
      />,
    ];
    if (items.length > 0 && state.canClearTemporary) {
      trailing.push(
        <_MusicBoxRowAction
          key="clear"
          data-testid="music-box-queue-clear"
          icon={Icons.delete_sweep_outlined}
          label="清空点歌队列"
          tone={ButtonTone.danger}
          onPressed={canWrite ? () => unawaited(this._confirmAndClear()) : null}
          size={26}
        />,
      );
    }
    return (
      <div ref={widget.headerKey} style={{ paddingBottom: 4 }}>
        <_MusicBoxSectionHeader
          channel="01"
          channelLit={lit}
          leading={<Icon icon={lit ? Icons.graphic_eq : Icons.queue_music} size={15} color={lit ? UiColors.accent : UiColors.textSecondary} />}
          title={this._headerTitle(items.length)}
          trailing={trailing}
        />
      </div>
    );
  }

  /** The search panel unfolded under the header: field plus source picker. */
  _searchPanel(): ReactNode {
    const widget = this.widget;
    return (
      <Row data-testid="music-box-search-panel" style={{ paddingBottom: 8 }}>
        <Expanded>
          <div data-testid="music-box-search-input">
            <Input
              controller={widget.searchController}
              focusNode={this._searchFocus}
              hintText="搜索歌曲点歌"
              prefixIcon={Icons.search}
              showClearButton
              maxLines={1}
              height={_musicBoxControlHeight}
            />
          </div>
        </Expanded>
        <SizedBox width={6} />
        <div data-testid="music-box-search-source" style={{ flexShrink: 0 }}>
          <SegmentedControl<string>
            height={_musicBoxControlHeight}
            value={music_box_display.normalizedMusicBoxSource(widget.source)}
            segments={music_box_display.musicBoxSources.map((source) => new Segment({ value: source.id, label: source.label }))}
            onChanged={widget.onSourceChanged}
          />
        </div>
      </Row>
    );
  }

  _queueRow(item: MusicBoxQueueItem, index: number, isCurrent: boolean): ReactNode {
    const widget = this.widget;
    const canWrite = widget.controller != null && widget.roomId != null;
    // Active queue: play-now needs playable audio (ready, or already
    // streaming) and the server's per-item permission. Inactive queue: any
    // song can switch the room back to the request queue, subject to the
    // switch capability.
    const canPlay = canWrite && !isCurrent && (this._isActive ? item.canPlayNow && item.playable : widget.state.canSwitchTemporary);
    const requester = item.requestedBy;
    const trailing: ReactNode[] = [];
    if (requester != null) {
      trailing.push(
        <_MusicBoxRequesterAvatar
          key="requester"
          data-testid={`music-box-queue-requester:${item.id}`}
          requester={requester}
          currentUser={widget.currentUser}
          onResolveUserProfile={widget.onResolveUserProfile}
          onResolveRoomProfile={widget.onResolveRoomProfile}
          onEnterCommonRoom={widget.onEnterCommonRoom}
          userProfileActionBuilder={widget.userProfileActionBuilder}
        />,
      );
    }
    if (canPlay) {
      trailing.push(
        <_MusicBoxRowAction
          key="play"
          data-testid={`music-box-queue-play-now:${item.id}`}
          icon={Icons.play_arrow}
          label={this._isActive ? '优先播放' : '从这首歌开始播放点歌队列'}
          tone={ButtonTone.primary}
          loading={this._playingNowItemId === item.id}
          onPressed={() => unawaited(this._playItem(item))}
        />,
      );
    }
    if (item.canRemove) {
      trailing.push(
        <_MusicBoxRowAction
          key="remove"
          data-testid={`music-box-queue-remove:${item.id}`}
          icon={Icons.close}
          label="从点歌队列删除"
          tone={ButtonTone.danger}
          onPressed={() => widget.onRemoveItem(item)}
        />,
      );
    }
    return (
      <_MusicBoxTrackRow
        data-testid={`music-box-queue-tile:${item.id}`}
        leading={<_MusicBoxQueueRowLeading item={item} index={index} isCurrent={isCurrent} />}
        title={item.title}
        subtitle={_musicBoxQueueSubtitle(item)}
        subtitleColor={item.status === MusicBoxQueueItemStatus.failed ? UiColors.danger : UiColors.textSecondary}
        selected={isCurrent}
        // Dart `Object.hash(...)` of everything the card shows.
        cardResetKey={JSON.stringify([
          item.id,
          item.status,
          item.playable,
          item.canPlayNow,
          requester?.avatarUrl ?? null,
          requester?.avatarLabel ?? null,
          widget.state.activeSource.type,
          widget.state.activeSource.name,
        ])}
        cardBuilder={() =>
          _MusicBoxSongCard.queue({
            item,
            isCurrent,
            queueSongCount: this._items.length,
            controller: widget.controller,
            roomId: widget.roomId,
            activeSource: widget.state.activeSource,
            onStateChanged: widget.onStateChanged,
            currentUser: widget.currentUser,
            onResolveUserProfile: widget.onResolveUserProfile,
            onResolveRoomProfile: widget.onResolveRoomProfile,
            onEnterCommonRoom: widget.onEnterCommonRoom,
            userProfileActionBuilder: widget.userProfileActionBuilder,
          })
        }
        trailing={trailing}
      />
    );
  }
}

export function _MusicBoxQueueSection(props: _MusicBoxQueueSectionProps) {
  const state = useViewState(() => new _MusicBoxQueueSectionState(), props);
  const widget = props;
  const items = state._items;
  const hasQuery = widget.searchOpen && widget.query.trim().length > 0;
  const currentItemId = state._isActive ? widget.state.playback.currentItemId : '';
  if (!hasQuery) {
    state._scheduleRevealCurrent(
      currentItemId,
      currentItemId.length === 0 ? -1 : items.findIndex((item) => item.id === currentItemId),
    );
  }
  let list: ReactNode;
  if (hasQuery) {
    list = (
      <_MusicBoxSearchResults
        results={widget.searchResults}
        query={widget.query}
        searching={widget.searching}
        error={widget.searchError}
        source={widget.source}
        controller={widget.controller}
        roomId={widget.roomId}
        temporaryQueue={widget.state.temporaryQueue}
        onQueueResult={widget.onQueueResult}
      />
    );
  } else if (items.length === 0) {
    list = _musicBoxSliverBox(<_MusicBoxEmpty icon={Icons.queue_music} message="点歌队列为空" hint="点击右上角的搜索按钮点歌" />);
  } else {
    list = (
      <div data-testid="music-box-queue-list">
        {items.map((item, index) => {
          const isCurrent = item.id.length > 0 && item.id === currentItemId;
          return (
            <div key={item.id.length > 0 ? item.id : `#${index}`} ref={isCurrent ? state._currentRowKey : undefined}>
              {state._queueRow(item, index, isCurrent)}
            </div>
          );
        })}
      </div>
    );
  }
  return (
    <div data-testid="music-box-section:queue">
      {state._header()}
      {widget.searchOpen ? state._searchPanel() : null}
      {list}
      <SizedBox height={8} />
    </div>
  );
}

export function _musicBoxQueueSubtitle(item: MusicBoxQueueItem): string {
  const artist = item.artist.trim().length === 0 ? '未知艺人' : item.artist.trim();
  const status = music_box_display.musicBoxQueueStatusLabel(item);
  return status == null ? artist : `${artist} · ${status}`;
}

// ---------------------------------------------------------------------------
// Saved playlist groups

export interface _MusicBoxPlaylistGroupProps {
  headerKey: RefObject<HTMLDivElement | null>;
  type: MusicBoxActiveSourceType;
  open: boolean;
  openPlaylistKey: string | null;
  state: MusicBoxState;
  controller: MusicBoxController | null;
  roomId: string | null;
  playlistsRevision: number;
  room: PublicRoom | null;
  currentUser: CurrentUser | null;
  onStateChanged: _ValueChanged<MusicBoxState> | null;
  onToggle: () => void;
  onTogglePlaylist: _ValueChanged<string>;
  onCreateFirstPlaylist: (() => void) | null;
  onEdit: MusicPlaylistEditCallback | null;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: _ValueChanged<PublicRoom> | null;
  userProfileActionBuilder: UserProfileActionBuilder | null;
}

/**
 * A collapsed group of saved playlists (room or personal). Loads its list the
 * first time it is opened; each playlist row can unfold its tracks in place.
 * If the active source belongs to this scope but is not in the list (someone
 * else's personal playlist, or a template deleted after activation) it is
 * pinned on top so the playing snapshot always has a row.
 */
export class _MusicBoxPlaylistGroupState extends ViewState<_MusicBoxPlaylistGroupProps> {
  _loading = false;
  _loaded = false;
  _error: string | null = null;
  _playlists: readonly PersonalMusicPlaylist[] = [];
  _loadGeneration = 0;

  /** Dart `widget.roomScoped`. */
  get _roomScoped(): boolean {
    return this.widget.type === MusicBoxActiveSourceType.roomPlaylist;
  }

  override initState(): void {
    if (this.widget.open) unawaited(this._load());
  }

  override didUpdateProps(oldWidget: _MusicBoxPlaylistGroupProps): void {
    const widget = this.widget;
    const identityChanged =
      oldWidget.type !== widget.type || oldWidget.roomId !== widget.roomId || oldWidget.controller !== widget.controller;
    if (identityChanged) {
      this._loaded = false;
      this._playlists = [];
    }
    const needsLoad = widget.open && (identityChanged || !this._loaded || oldWidget.playlistsRevision !== widget.playlistsRevision);
    if (needsLoad && !this._loading) {
      unawaited(this._load());
    } else if (identityChanged) {
      // Flutter rebuilt right after didUpdateWidget; React needs the nudge.
      this.setState();
    }
  }

  async _load(): Promise<void> {
    const generation = ++this._loadGeneration;
    const controller = this.widget.controller;
    const roomId = this.widget.roomId;
    if (controller == null || (this._roomScoped && roomId == null)) {
      this.setState(() => {
        this._loading = false;
        this._loaded = true;
        this._error = '歌单服务暂不可用';
      });
      return;
    }
    this.setState(() => {
      this._loading = true;
      this._error = null;
    });
    try {
      const page = this._roomScoped ? await controller.loadRoomPlaylists({ roomId: roomId! }) : await controller.loadMyPlaylists();
      if (!this.mounted || generation !== this._loadGeneration) return;
      this.setState(() => {
        this._loading = false;
        this._loaded = true;
        this._playlists = page.playlists;
      });
    } catch {
      if (!this.mounted || generation !== this._loadGeneration) return;
      this.setState(() => {
        this._loading = false;
        this._loaded = true;
        this._error = '加载歌单失败';
      });
    }
  }

  _leadingCard(playlist: PersonalMusicPlaylist, playing: boolean, icon: ReactNode): ReactNode {
    const widget = this.widget;
    const active = widget.state.activeSource;
    const isActive = music_box_display.musicBoxSourceIsActive(widget.state, widget.type, playlist.id);
    const creator = this._roomScoped
      ? null
      : isActive
        ? _musicBoxActiveSourceOwner(active, widget.currentUser)
        : (widget.currentUser?.toSummary() ?? null);
    const onEdit = widget.onEdit;
    return (
      <span data-testid={`music-box-playlist-card-anchor:${playlist.id}`} style={{ display: 'flex' }}>
        <MusicPlaylistHoverCard
          data={
            new MusicPlaylistCardData({
              id: playlist.id,
              name: playlist.name,
              songCount: playlist.itemCount,
              createdAt: playlist.createdAt ?? (isActive ? active.createdAt : null),
              creator,
              room: this._roomScoped ? widget.room : null,
              showPlayingStatus: playing,
            })
          }
          currentUser={widget.currentUser ?? undefined}
          onResolveUserProfile={widget.onResolveUserProfile ?? undefined}
          onResolveRoomProfile={widget.onResolveRoomProfile ?? undefined}
          onEnterCommonRoom={widget.onEnterCommonRoom ?? undefined}
          userProfileActionBuilder={widget.userProfileActionBuilder ?? undefined}
          // Playback starts from a song inside the playlist, never from the
          // playlist as a whole: the card offers no play-all.
          onPlayAll={null}
          showPrimaryActionWhenDisabled={false}
          onViewPlaylist={onEdit == null ? async () => widget.onTogglePlaylist(playlist.id) : () => onEdit(playlist.id)}
          secondaryActionLabel={onEdit == null ? '查看歌单' : '编辑歌单'}
          secondaryActionIcon={onEdit == null ? Icons.queue_music : Icons.edit_outlined}
        >
          <SizedBox width={22} height={22}>
            <Center style={{ width: '100%', height: '100%' }}>{icon}</Center>
          </SizedBox>
        </MusicPlaylistHoverCard>
      </span>
    );
  }

  _entries(): PersonalMusicPlaylist[] {
    const state = this.widget.state;
    const active = state.activeSource;
    const activeInScope = active.type === this.widget.type && active.id.length > 0;
    const activeListed = activeInScope && this._playlists.some((playlist) => playlist.id === active.id);
    return [
      ...(activeInScope && !activeListed
        ? [
            new PersonalMusicPlaylist({
              id: active.id,
              name: music_box_display.musicBoxActiveSourceLabel(active),
              description: '',
              revision: 0,
              itemCount: state.queue.length,
              createdAt: active.createdAt,
              updatedAt: null,
            }),
          ]
        : []),
      ...this._playlists,
    ];
  }

  _body(): ReactNode[] {
    if (this._loading && !this._loaded) {
      return [_musicBoxSliverBox(<_MusicBoxLoading />, { key: 'loading' })];
    }
    const entries = this._entries();
    const error = this._error;
    if (error != null) {
      // The playing snapshot needs no list load, so its pinned row stays
      // reachable under the retry state.
      return [
        _musicBoxSliverBox(<_MusicBoxRetryState message={error} onRetry={() => unawaited(this._load())} />, { key: 'retry' }),
        ...this._rows(entries),
      ];
    }
    if (entries.length === 0) {
      const roomScoped = this._roomScoped;
      return [
        _musicBoxSliverBox(
          <_MusicBoxEmpty
            icon={roomScoped ? Icons.meeting_room : Icons.person}
            message={roomScoped ? '还没有房间歌单' : '还没有个人歌单'}
            actionKey={roomScoped ? 'music-box-create-first-room-playlist' : 'music-box-create-first-personal-playlist'}
            actionLabel={this.widget.onCreateFirstPlaylist == null ? null : '新建第一个歌单'}
            onAction={this.widget.onCreateFirstPlaylist}
          />,
          { height: 140, key: 'empty' },
        ),
      ];
    }
    return this._rows(entries);
  }

  _rows(entries: readonly PersonalMusicPlaylist[]): ReactNode[] {
    const state = this.widget.state;
    const active = state.activeSource;
    const hasActivePlayback = music_box_display.musicBoxHasActivePlayback(state);
    const typeValue = musicBoxActiveSourceTypeValue(this.widget.type);
    return [
      ...entries.map((playlist) =>
        this._playlistBlock(playlist, {
          typeValue,
          isActive: active.type === this.widget.type && playlist.id === active.id,
          hasActivePlayback,
        }),
      ),
      <SizedBox key="tail" height={4} />,
    ];
  }

  /**
   * One playlist and, when unfolded, its tracks — framed as a single block
   * with a gap below it, so neighbouring playlists and the next group read
   * as separate things.
   */
  _playlistBlock(
    playlist: PersonalMusicPlaylist,
    opts: { typeValue: string; isActive: boolean; hasActivePlayback: boolean },
  ): ReactNode {
    const { typeValue, isActive, hasActivePlayback } = opts;
    const widget = this.widget;
    const playing = isActive && hasActivePlayback;
    const open = widget.openPlaylistKey === music_box_display.musicBoxPlaylistKey(widget.type, playlist.id);
    const row = (
      <_MusicBoxPlaylistRow
        data-testid={`music-box-playlist-row:${typeValue}:${playlist.id}`}
        name={playlist.name}
        itemCount={playlist.itemCount}
        playing={playing}
        open={open}
        icon={this._roomScoped ? Icons.meeting_room : Icons.person}
        onOpen={() => widget.onTogglePlaylist(playlist.id)}
        hoverColor={UiColors.surface}
        leadingCard={(icon) => this._leadingCard(playlist, playing, icon)}
      />
    );
    // Every playlist is the same framed block; unfolding only adds the
    // tracks inside it, so folded and unfolded rows read as one kind of thing.
    // PORT: keyed by playlist id (the Dart SliverPadding was matched by
    // position), so an unfolded block keeps its tracks state when a pinned
    // entry appears above it.
    return (
      <div key={playlist.id} style={{ paddingBottom: 6 }}>
        <div
          style={{
            backgroundColor: UiColors.surfaceLow,
            borderRadius: UiRadii.md,
            border: `1px solid ${open ? UiColors.borderStrong : UiColors.border}`,
            // DecoratedSliver does not inset for its border: SliverPadding(3)
            // is measured from the outer edge, so 1px border + 2px padding.
            padding: 2,
            boxSizing: 'border-box',
          }}
        >
          {row}
          {open ? (
            <>
              <SizedBox height={2} />
              <_MusicBoxPlaylistTracks
                key={`music-box-playlist-tracks:${playlist.id}`}
                type={widget.type}
                playlistId={playlist.id}
                state={widget.state}
                controller={widget.controller}
                roomId={widget.roomId}
                playlistsRevision={widget.playlistsRevision}
                onStateChanged={widget.onStateChanged}
                currentUser={widget.currentUser}
                onResolveUserProfile={widget.onResolveUserProfile}
                onResolveRoomProfile={widget.onResolveRoomProfile}
                onEnterCommonRoom={widget.onEnterCommonRoom}
                userProfileActionBuilder={widget.userProfileActionBuilder}
              />
              <SizedBox height={3} />
            </>
          ) : null}
        </div>
      </div>
    );
  }
}

export function _MusicBoxPlaylistGroup(props: _MusicBoxPlaylistGroupProps) {
  const state = useViewState(() => new _MusicBoxPlaylistGroupState(), props);
  const widget = props;
  const active = widget.state.activeSource;
  const playing = active.type === widget.type && music_box_display.musicBoxHasActivePlayback(widget.state);
  const roomScoped = state._roomScoped;
  const typeValue = musicBoxActiveSourceTypeValue(widget.type);
  return (
    <div data-testid={`music-box-group:${typeValue}`}>
      <div ref={widget.headerKey}>
        <_MusicBoxGroupHeader
          data-testid={`music-box-group-header:${typeValue}`}
          channel={roomScoped ? '02' : '03'}
          title={roomScoped ? '房间歌单' : '我的歌单'}
          icon={roomScoped ? Icons.meeting_room : Icons.person}
          open={widget.open}
          playing={playing}
          count={state._loaded && state._error == null ? state._entries().length : null}
          onToggle={widget.onToggle}
        />
      </div>
      {widget.open ? (
        <>
          <SizedBox height={2} />
          {state._body()}
        </>
      ) : null}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Tracks of an unfolded playlist

export interface _MusicBoxPlaylistTracksProps {
  type: MusicBoxActiveSourceType;
  playlistId: string;
  state: MusicBoxState;
  controller: MusicBoxController | null;
  roomId: string | null;
  playlistsRevision: number;
  onStateChanged: _ValueChanged<MusicBoxState> | null;
  currentUser: CurrentUser | null;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: _ValueChanged<PublicRoom> | null;
  userProfileActionBuilder: UserProfileActionBuilder | null;
}

/**
 * The tracks of one unfolded playlist, indented under its row. When the
 * playlist is the active source the rows are the authoritative activation
 * snapshot (live status, play-now); otherwise the saved template is loaded
 * and each row can start the playlist from that song. Presets are played
 * from here and edited elsewhere, so there are no request/add actions.
 */
export class _MusicBoxPlaylistTracksState extends ViewState<_MusicBoxPlaylistTracksProps> {
  static readonly _indent = 12;

  _loading = false;
  _error: string | null = null;
  _items: readonly PersonalMusicPlaylistItem[] = [];
  _loadGeneration = 0;
  _startingItemId: string | null = null;

  /** Dart `widget.roomScoped`. */
  get _roomScoped(): boolean {
    return this.widget.type === MusicBoxActiveSourceType.roomPlaylist;
  }

  get _isActive(): boolean {
    return music_box_display.musicBoxSourceIsActive(this.widget.state, this.widget.type, this.widget.playlistId);
  }

  override initState(): void {
    if (!this._isActive) unawaited(this._load());
  }

  override didUpdateProps(oldWidget: _MusicBoxPlaylistTracksProps): void {
    const widget = this.widget;
    const wasActive = music_box_display.musicBoxSourceIsActive(oldWidget.state, oldWidget.type, oldWidget.playlistId);
    if (
      oldWidget.playlistId !== widget.playlistId ||
      oldWidget.type !== widget.type ||
      oldWidget.roomId !== widget.roomId ||
      oldWidget.controller !== widget.controller ||
      oldWidget.playlistsRevision !== widget.playlistsRevision ||
      (wasActive && !this._isActive)
    ) {
      if (!this._isActive) unawaited(this._load());
    }
  }

  async _load(): Promise<void> {
    const generation = ++this._loadGeneration;
    const controller = this.widget.controller;
    const roomId = this.widget.roomId;
    if (controller == null || (this._roomScoped && roomId == null)) {
      this.setState(() => {
        this._loading = false;
        this._error = '歌单服务暂不可用';
      });
      return;
    }
    this.setState(() => {
      this._loading = true;
      this._error = null;
    });
    try {
      const page = this._roomScoped
        ? await controller.loadRoomPlaylist({ roomId: roomId!, playlistId: this.widget.playlistId })
        : await controller.loadMyPlaylist({ playlistId: this.widget.playlistId });
      if (!this.mounted || generation !== this._loadGeneration) return;
      this.setState(() => {
        this._loading = false;
        this._items = page.items;
      });
    } catch {
      if (!this.mounted || generation !== this._loadGeneration) return;
      this.setState(() => {
        this._loading = false;
        this._error = '加载歌曲失败';
      });
    }
  }

  async _activateFrom(item: PersonalMusicPlaylistItem): Promise<void> {
    const controller = this.widget.controller;
    const roomId = this.widget.roomId;
    if (controller == null || roomId == null || this._startingItemId != null) {
      return;
    }
    this.setState(() => {
      this._startingItemId = item.id;
    });
    await _musicBoxRunWrite(this, {
      write: () =>
        controller.activatePlaylist({
          roomId,
          sourceType: this.widget.type,
          playlistId: this.widget.playlistId,
          startItemId: item.id,
        }),
      onStateChanged: this.widget.onStateChanged,
      failureMessage: '播放歌曲失败，请重试',
    });
    if (this.mounted) {
      this.setState(() => {
        this._startingItemId = null;
      });
    }
  }

  async _playNow(item: MusicBoxQueueItem): Promise<void> {
    const controller = this.widget.controller;
    const roomId = this.widget.roomId;
    if (controller == null || roomId == null || this._startingItemId != null) {
      return;
    }
    this.setState(() => {
      this._startingItemId = item.id;
    });
    await _musicBoxRunWrite(this, {
      write: () => controller.playNow({ roomId, item }),
      onStateChanged: this.widget.onStateChanged,
      failureMessage: '优先播放失败，请重试',
      successMessage: '已优先播放',
    });
    if (this.mounted) {
      this.setState(() => {
        this._startingItemId = null;
      });
    }
  }

  _indented(row: ReactNode, key: string): ReactNode {
    return (
      <div key={key} style={{ paddingLeft: _MusicBoxPlaylistTracksState._indent }}>
        {row}
      </div>
    );
  }

  _snapshotRow(item: MusicBoxQueueItem, index: number): ReactNode {
    const widget = this.widget;
    const state = widget.state;
    const isCurrent = item.id.length > 0 && item.id === state.playback.currentItemId;
    const canPlayNow = widget.controller != null && widget.roomId != null && item.canPlayNow && item.playable && !isCurrent;
    return this._indented(
      <_MusicBoxTrackRow
        data-testid={`music-box-queue-tile:${item.id}`}
        hoverColor={UiColors.surface}
        leading={<_MusicBoxQueueRowLeading item={item} index={index} isCurrent={isCurrent} />}
        title={item.title}
        subtitle={_musicBoxQueueSubtitle(item)}
        subtitleColor={item.status === MusicBoxQueueItemStatus.failed ? UiColors.danger : UiColors.textSecondary}
        selected={isCurrent}
        cardResetKey={JSON.stringify([item.id, item.status, item.playable, item.canPlayNow])}
        cardBuilder={() =>
          _MusicBoxSongCard.queue({
            item,
            isCurrent,
            queueSongCount: state.queue.length,
            controller: widget.controller,
            roomId: widget.roomId,
            activeSource: state.activeSource,
            onStateChanged: widget.onStateChanged,
            currentUser: widget.currentUser,
            onResolveUserProfile: widget.onResolveUserProfile,
            onResolveRoomProfile: widget.onResolveRoomProfile,
            onEnterCommonRoom: widget.onEnterCommonRoom,
            userProfileActionBuilder: widget.userProfileActionBuilder,
          })
        }
        trailing={
          canPlayNow
            ? [
                <_MusicBoxRowAction
                  key="play"
                  data-testid={`music-box-queue-play-now:${item.id}`}
                  icon={Icons.play_arrow}
                  label="优先播放"
                  tone={ButtonTone.primary}
                  loading={this._startingItemId === item.id}
                  onPressed={() => unawaited(this._playNow(item))}
                />,
              ]
            : []
        }
      />,
      item.id.length > 0 ? item.id : `#${index}`,
    );
  }

  _templateRow(item: PersonalMusicPlaylistItem, index: number): ReactNode {
    const widget = this.widget;
    const result = _musicBoxPlaylistItemAsResult(item);
    const identity = `${item.source}:${item.trackId}`;
    const artists = music_box_display.musicBoxArtistsLabel(item.artists);
    const canWrite = widget.controller != null && widget.roomId != null;
    return this._indented(
      <_MusicBoxTrackRow
        data-testid={`music-box-playlist-track:${identity}`}
        hoverColor={UiColors.surface}
        leading={
          <Text
            softWrap={false}
            style={{
              fontFamily: kClientFontStack,
              color: UiColors.textMuted,
              fontSize: 11,
              fontWeight: 600,
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {`${index + 1}`}
          </Text>
        }
        title={item.title}
        subtitle={artists.length === 0 ? '未知艺人' : artists}
        cardResetKey={identity}
        // Information only: presets are not requested from here.
        cardBuilder={() =>
          _MusicBoxSongCard.search({
            result,
            controller: widget.controller,
            roomId: widget.roomId,
            onQueueResult: null,
            alreadyInRequestQueue: false,
            catalogDurationMs: item.durationMs,
          })
        }
        trailing={[
          <_MusicBoxRowAction
            key="play"
            data-testid={`music-box-playlist-track-play:${identity}`}
            icon={Icons.play_arrow}
            label="从这首歌开始播放歌单"
            tone={ButtonTone.primary}
            loading={this._startingItemId === item.id}
            onPressed={canWrite ? () => unawaited(this._activateFrom(item)) : null}
          />,
        ]}
      />,
      `${item.id}:${index}`,
    );
  }
}

export function _MusicBoxPlaylistTracks(props: _MusicBoxPlaylistTracksProps) {
  const state = useViewState(() => new _MusicBoxPlaylistTracksState(), props);
  const testId = `music-box-playlist-tracks:${props.playlistId}`;
  let body: ReactNode;
  if (state._isActive) {
    const queue = props.state.queue;
    if (queue.length === 0) {
      body = _musicBoxSliverBox(<_MusicBoxEmpty icon={Icons.queue_music} message="这个歌单还没有歌曲" />, { height: 96 });
    } else {
      body = queue.map((item, index) => state._snapshotRow(item, index));
    }
  } else if (state._loading) {
    body = _musicBoxSliverBox(<_MusicBoxLoading />, { height: 96 });
  } else if (state._error != null) {
    body = _musicBoxSliverBox(<_MusicBoxRetryState message={state._error} onRetry={() => unawaited(state._load())} />);
  } else if (state._items.length === 0) {
    body = _musicBoxSliverBox(<_MusicBoxEmpty icon={Icons.queue_music} message="这个歌单还没有歌曲" />, { height: 96 });
  } else {
    body = state._items.map((item, index) => state._templateRow(item, index));
  }
  return <div data-testid={testId}>{body}</div>;
}

// ---------------------------------------------------------------------------
// Search results

export interface _MusicBoxSearchResultsProps {
  results: readonly MusicBoxSearchResult[];
  query: string;
  searching: boolean;
  error: string | null;
  source: string;
  controller: MusicBoxController | null;
  roomId: string | null;
  temporaryQueue: readonly MusicBoxQueueItem[];
  onQueueResult: _ValueChanged<MusicBoxSearchResult>;
}

/**
 * Search hits for the current query, rendered under the queue header. The
 * only action is "加入点歌队列"; a track already in the queue shows a
 * disabled check instead of failing after the tap.
 */
export function _MusicBoxSearchResults({
  results,
  query,
  searching,
  error,
  source,
  controller,
  roomId,
  temporaryQueue,
  onQueueResult,
}: _MusicBoxSearchResultsProps) {
  const message = error?.trim() ?? null;
  const sourceLabel = music_box_display.musicBoxSourceLabel(music_box_display.normalizedMusicBoxSource(source));

  const _row = (result: MusicBoxSearchResult): ReactNode => {
    const identity = `${result.source}:${result.trackId}`;
    const artists = music_box_display.musicBoxArtistsLabel(result.artists);
    const alreadyQueued = music_box_display.musicBoxRequestQueueContainsTrack(temporaryQueue, {
      source: result.source,
      trackId: result.trackId,
    });
    return (
      <_MusicBoxTrackRow
        key={identity}
        data-testid={`music-box-search-tile:${identity}`}
        leading={<Icon icon={Icons.music_note} size={18} color={UiColors.accent} />}
        title={result.name}
        subtitle={artists.length === 0 ? '未知艺人' : artists}
        query={query}
        cardResetKey={`${identity}:${alreadyQueued}`}
        cardBuilder={() =>
          _MusicBoxSongCard.search({
            result,
            controller,
            roomId,
            onQueueResult: alreadyQueued ? null : onQueueResult,
            alreadyInRequestQueue: alreadyQueued,
          })
        }
        trailing={[
          <_MusicBoxRowAction
            key="add"
            data-testid={`music-box-search-add:${identity}`}
            icon={alreadyQueued ? Icons.playlist_add_check : Icons.add}
            label={alreadyQueued ? '已在点歌队列' : '加入点歌队列'}
            tone={alreadyQueued ? ButtonTone.neutral : ButtonTone.primary}
            onPressed={alreadyQueued ? null : () => onQueueResult(result)}
          />,
        ]}
      />
    );
  };

  let body: ReactNode;
  if (searching && results.length === 0) {
    body = _musicBoxSliverBox(<_MusicBoxLoading />);
  } else if (message != null && message.length > 0) {
    body = _musicBoxSliverBox(<_MusicBoxEmpty icon={Icons.error_outline} message="搜索失败" hint={message} />);
  } else if (results.length === 0) {
    body = _musicBoxSliverBox(
      <_MusicBoxEmpty icon={Icons.search_off} message="没有找到相关歌曲" hint="换个关键词，或切换搜索来源试试" />,
    );
  } else {
    body = <div data-testid="music-box-search-results-list">{results.map((result) => _row(result))}</div>;
  }
  return (
    <>
      <div style={{ paddingBottom: 4 }}>
        <_MusicBoxSectionHeader
          title={searching ? `正在搜索 ${sourceLabel}…` : `搜索结果 · ${sourceLabel} · ${results.length} 首`}
        />
      </div>
      {body}
    </>
  );
}

