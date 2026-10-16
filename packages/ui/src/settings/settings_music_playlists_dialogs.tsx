import { useRef, useSyncExternalStore, type ReactNode } from 'react';

import { publicRoomFromRoomDetail } from '@gang-chat/core/app/room_display';
import { musicBoxDefaultSource, musicBoxSources } from '@gang-chat/core/app/music_box_display';
import type { MusicTrackPreviewController } from '@gang-chat/core/app/music_track_preview';
import {
  normalizedPersonalPlaylistName,
  PersonalMusicPlaylistsController,
  PersonalPlaylistCreateDraft,
  PersonalPlaylistFilterDraft,
  PersonalPlaylistItemFilterDraft,
  personalPlaylistCount11To50,
  personalPlaylistCount1To10,
  personalPlaylistCount51To100,
  personalPlaylistCountAll,
  personalPlaylistCountEmpty,
  personalPlaylistCountOver100,
} from '@gang-chat/core/app/personal_music_playlists';
import { Timer, unawaited } from '@gang-chat/core/lib/async';
import type { GangApi } from '@gang-chat/core/protocol/api_client';
import {
  PublicRoom,
  type CurrentUser,
  type MusicBoxSearchResult,
  type PersonalMusicPlaylist,
  type PersonalMusicPlaylistPage,
  type RoomCard,
} from '@gang-chat/core/protocol/models';

import { Center, Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { FocusNode, TextEditingController, TextInputAction, TextSelection } from '../lib/text_editing';
import { measureLineCount } from '../lib/text_measure';
import { useElementSize } from '../lib/use_element_size';
import { useViewState, ViewState } from '../lib/view_state';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { Button, ButtonIcon, ButtonTone } from '../ui/button';
import { DialogFrame, ResponsiveDialogAction } from '../ui/feedback';
import { Icon, Icons, type IconData } from '../ui/icon';
import { Input } from '../ui/input';
import { showDialog, useRoute } from '../ui/overlay';
import { Segment, SegmentedControl } from '../ui/segmented_control';
import { PressableSurface } from '../ui/surface';
import { UiColors } from '../ui/tokens';
import type { MusicTrackPlaylistTarget } from '../home/music_track_profile_card';
import { RoomHoverCard } from '../home/room_profile_card';
import { _SettingsSubPanel } from './settings_components';
import { _SettingsEmptyState } from './settings_profile_widgets';
import { _borderColor, _cyan, _primaryDark, _primaryDarkLow, _textMuted, _textPrimary, _textSecondary } from './settings_page';
import {
  _ManagementCardTapTarget,
  _MusicPlaylistErrorText,
  _MusicPlaylistFieldLabel,
  _MusicPlaylistSearchResultTile,
  _MusicPlaylistSearchRow,
} from './settings_music_playlists_tiles';

// Part of the settings page (Dart `part of 'settings_page.dart'`): the
// dialogs the playlist manager opens. Colour aliases from `./settings_page`
// are only read while rendering (see settings_music_playlists_tiles.tsx).

/**
 * Port of `MediaQuery.sizeOf(context).height`: the window's height, kept
 * current across resizes. The desktop window has no on-screen keyboard, so
 * Dart's `viewInsets.bottom` is always 0 here.
 */
function _useWindowHeight(): number {
  return useSyncExternalStore(_subscribeWindowResize, _windowHeight, _windowHeight);
}

function _subscribeWindowResize(onChange: () => void): () => void {
  window.addEventListener('resize', onChange);
  return () => window.removeEventListener('resize', onChange);
}

function _windowHeight(): number {
  return typeof window === 'undefined' ? 0 : window.innerHeight;
}

function _clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

/** Fills the remaining height of a dialog column (Flutter `Expanded`). */
function _ExpandedFill({ children }: { children?: ReactNode }) {
  return <Expanded style={{ flex: '1 1 0' }}>{children}</Expanded>;
}

/**
 * Flutter forced the empty state's 74px box to the full height of the
 * `Expanded` it sat in, centring the text in the remaining space. The DOM
 * port centres the box there instead, which draws the same.
 */
function _CenteredEmptyState({ text }: { text: string }) {
  return (
    <Center style={{ flex: '1 1 0', minHeight: 0, flexDirection: 'column', alignItems: 'stretch' }}>
      <_SettingsEmptyState text={text} />
    </Center>
  );
}

function _CenteredProgress() {
  return (
    <Center style={{ flex: '1 1 0', minHeight: 0 }}>
      <CircularProgressIndicator color={_cyan} />
    </Center>
  );
}

// ---------------------------------------------------------------------------
// 搜索添加

export interface _MusicPlaylistTrackSearchDialogProps {
  controller: PersonalMusicPlaylistsController;
  playlistId: string;
  previewController: MusicTrackPreviewController | null;
  loadPlaylistTargets: () => Promise<MusicTrackPlaylistTarget[]>;
  onAddToPlaylistTarget: (track: MusicBoxSearchResult, target: MusicTrackPlaylistTarget) => Promise<void>;
  onTrackAdded: () => Promise<void>;
}

export class _MusicPlaylistTrackSearchDialogState extends ViewState<_MusicPlaylistTrackSearchDialogProps> {
  readonly _searchController = new TextEditingController();
  readonly _searchFocusNode = new FocusNode();
  readonly _busyTrackKeys = new Set<string>();
  _debounce: Timer | null = null;
  _results: MusicBoxSearchResult[] = [];
  _source: string = musicBoxDefaultSource;
  _error: string | null = null;
  _generation = 0;
  _searching = false;

  override initState(): void {
    this._searchController.addListener(this._handleSearchChanged);
    // Dart focused in a post-frame callback; the FocusNode queues the request
    // until the field has mounted.
    this._searchFocusNode.requestFocus();
  }

  override dispose(): void {
    this._generation += 1;
    this._debounce?.cancel();
    this._searchController.removeListener(this._handleSearchChanged);
    this._searchController.dispose();
    this._searchFocusNode.dispose();
  }

  _handleSearchChanged = (): void => this._scheduleSearch();

  _scheduleSearch(opts: { immediate?: boolean } = {}): void {
    const { immediate = false } = opts;
    this._debounce?.cancel();
    const keyword = this._searchController.text.trim();
    const source = this._source;
    const generation = ++this._generation;
    if (keyword.length === 0) {
      this.setState(() => {
        this._results = [];
        this._searching = false;
        this._error = null;
      });
      return;
    }
    this.setState(() => {
      this._searching = true;
      this._results = [];
      this._error = null;
    });
    if (immediate) {
      unawaited(this._runSearch({ keyword, source, generation }));
      return;
    }
    this._debounce = new Timer(350, () => unawaited(this._runSearch({ keyword, source, generation })));
  }

  _submitSearch = async (): Promise<void> => {
    this._debounce?.cancel();
    const keyword = this._searchController.text.trim();
    if (keyword.length === 0) {
      this._scheduleSearch({ immediate: true });
      return;
    }
    const generation = ++this._generation;
    this.setState(() => {
      this._searching = true;
      this._results = [];
      this._error = null;
    });
    await this._runSearch({ keyword, source: this._source, generation });
  };

  async _runSearch(opts: { keyword: string; source: string; generation: number }): Promise<void> {
    const { keyword, source, generation } = opts;
    try {
      const results = await this.widget.controller.searchTracks({ keyword, source });
      if (
        !this.mounted ||
        generation !== this._generation ||
        this._searchController.text.trim() !== keyword ||
        this._source !== source ||
        results == null
      ) {
        return;
      }
      this.setState(() => {
        this._results = results;
      });
    } catch (error) {
      if (!this.mounted || generation !== this._generation) return;
      this.setState(() => {
        this._results = [];
        this._error = String(error);
      });
    } finally {
      if (this.mounted && generation === this._generation) {
        this.setState(() => {
          this._searching = false;
        });
      }
    }
  }

  _setSource = (source: string): void => {
    if (this._source === source) return;
    this._debounce?.cancel();
    this._generation += 1;
    this.setState(() => {
      this._source = source;
      this._results = [];
      this._searching = false;
      this._error = null;
    });
    if (this._searchController.text.trim().length > 0) {
      this._scheduleSearch({ immediate: true });
    }
  };

  async _addTrack(track: MusicBoxSearchResult): Promise<void> {
    const key = `${track.source}:${track.trackId}`;
    if (this._busyTrackKeys.has(key)) return;
    this.setState(() => {
      this._busyTrackKeys.add(key);
      this._error = null;
    });
    try {
      await this.widget.controller.addTrack({ playlistId: this.widget.playlistId, track });
      await this.widget.onTrackAdded();
    } catch (error) {
      if (this.mounted) {
        this.setState(() => {
          this._error = String(error);
        });
      }
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._busyTrackKeys.delete(key);
        });
      }
    }
  }
}

export function _MusicPlaylistTrackSearchDialog(props: _MusicPlaylistTrackSearchDialogProps) {
  const state = useViewState(() => new _MusicPlaylistTrackSearchDialogState(), props);
  const route = useRoute<void>();
  const windowHeight = _useWindowHeight();
  const contentHeight = _clamp(windowHeight - 0 - 220, 180.0, 520.0);
  const query = state._searchController.text;
  let results: ReactNode;
  if (state._searching && state._results.length === 0) {
    results = <_CenteredProgress />;
  } else if (state._results.length === 0) {
    results = <_CenteredEmptyState text={query.trim().length === 0 ? '输入歌名或歌手后自动搜索' : '没有找到相关歌曲'} />;
  } else {
    results = (
      <div style={{ flex: '1 1 0', minHeight: 0, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 10 }}>
        {state._results.map((result) => {
          const key = `${result.source}:${result.trackId}`;
          return (
            <div key={key} style={{ flexShrink: 0, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
              <_MusicPlaylistSearchResultTile
                result={result}
                query={query}
                loading={state._busyTrackKeys.has(key)}
                onAdd={() => unawaited(state._addTrack(result))}
                previewController={props.previewController}
                loadPlaylistTargets={props.loadPlaylistTargets}
                onAddToPlaylistTarget={props.onAddToPlaylistTarget}
              />
            </div>
          );
        })}
      </div>
    );
  }
  return (
    <DialogFrame
      title="搜索添加"
      icon={Icons.playlist_add}
      maxWidth={680}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '完成', icon: Icons.check, tone: ButtonTone.primary, onPressed: () => route.close() }),
      ]}
    >
      <SizedBox height={contentHeight} style={{ display: 'flex', flexDirection: 'column' }}>
        <Column crossAxisAlignment="stretch" style={{ flex: '1 1 0', minHeight: 0 }}>
          <SegmentedControl<string>
            expanded
            value={state._source}
            onChanged={state._setSource}
            segments={musicBoxSources.map((source) => new Segment<string>({ value: source.id, label: source.label }))}
          />
          <SizedBox height={10} />
          <_MusicPlaylistSearchRow
            controller={state._searchController}
            focusNode={state._searchFocusNode}
            hintText="搜索歌曲添加到歌单"
            onSubmitted={() => unawaited(state._submitSearch())}
          />
          {state._error != null ? (
            <>
              <SizedBox height={10} />
              <_MusicPlaylistErrorText message={state._error} />
            </>
          ) : null}
          <SizedBox height={10} />
          <_ExpandedFill>{results}</_ExpandedFill>
        </Column>
      </SizedBox>
    </DialogFrame>
  );
}

// ---------------------------------------------------------------------------
// 筛选歌曲

export interface _MusicPlaylistItemFilterDialogProps {
  keyword: string;
  source: string;
}

export class _MusicPlaylistItemFilterDialogState extends ViewState<_MusicPlaylistItemFilterDialogProps> {
  constructor(props: _MusicPlaylistItemFilterDialogProps) {
    super();
    this._keywordController = new TextEditingController({ text: props.keyword });
    this._source = props.source;
  }

  readonly _keywordController: TextEditingController;
  readonly _keywordFocusNode = new FocusNode();
  _source: string;

  override initState(): void {
    this._keywordFocusNode.requestFocus();
  }

  override dispose(): void {
    this._keywordController.dispose();
    this._keywordFocusNode.dispose();
  }
}

export function _MusicPlaylistItemFilterDialog(props: _MusicPlaylistItemFilterDialogProps) {
  const state = useViewState(() => new _MusicPlaylistItemFilterDialogState(props), props);
  const route = useRoute<PersonalPlaylistItemFilterDraft>();
  return (
    <DialogFrame
      title="筛选歌曲"
      icon={Icons.filter_alt_outlined}
      adaptiveActions={[
        new ResponsiveDialogAction({
          label: '重置',
          icon: Icons.restart_alt,
          onPressed: () => {
            state._keywordController.clear();
            state.setState(() => {
              state._source = '';
            });
          },
        }),
        new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close() }),
        new ResponsiveDialogAction({
          label: '确认',
          icon: Icons.check,
          tone: ButtonTone.primary,
          onPressed: () =>
            route.close(
              new PersonalPlaylistItemFilterDraft({ keyword: state._keywordController.text.trim(), source: state._source }),
            ),
        }),
      ]}
    >
      <Column mainAxisSize="min" crossAxisAlignment="stretch">
        <_MusicPlaylistFieldLabel text="名称关键字" />
        <SizedBox height={8} />
        <Input
          controller={state._keywordController}
          focusNode={state._keywordFocusNode}
          hintText=""
          showClearButton
          minLines={1}
          maxLines={1}
        />
        <SizedBox height={16} />
        <_MusicPlaylistFieldLabel text="歌曲来源" />
        <SizedBox height={8} />
        <SegmentedControl<string>
          expanded
          value={state._source}
          onChanged={(source) =>
            state.setState(() => {
              state._source = source;
            })
          }
          segments={[
            new Segment<string>({ value: '', label: '全部' }),
            ...musicBoxSources.map((source) => new Segment<string>({ value: source.id, label: source.label })),
          ]}
        />
      </Column>
    </DialogFrame>
  );
}

// ---------------------------------------------------------------------------
// 筛选（歌单列表）

export class _MusicPlaylistCountFilter {
  constructor(
    readonly value: string,
    readonly label: string,
  ) {}
}

export interface _MusicPlaylistFilterDialogProps {
  keyword: string;
  countFilter: string;
}

export class _MusicPlaylistFilterDialogState extends ViewState<_MusicPlaylistFilterDialogProps> {
  static readonly _countFilters: readonly _MusicPlaylistCountFilter[] = [
    new _MusicPlaylistCountFilter(personalPlaylistCountAll, '全部'),
    new _MusicPlaylistCountFilter(personalPlaylistCountEmpty, '空歌单'),
    new _MusicPlaylistCountFilter(personalPlaylistCount1To10, '1～10 首'),
    new _MusicPlaylistCountFilter(personalPlaylistCount11To50, '11～50 首'),
    new _MusicPlaylistCountFilter(personalPlaylistCount51To100, '51～100 首'),
    new _MusicPlaylistCountFilter(personalPlaylistCountOver100, '100 首以上'),
  ];

  constructor(props: _MusicPlaylistFilterDialogProps) {
    super();
    this._keywordController = new TextEditingController({ text: props.keyword });
    this._countFilter = props.countFilter;
  }

  readonly _keywordController: TextEditingController;
  readonly _keywordFocusNode = new FocusNode();
  _countFilter: string;

  override initState(): void {
    this._keywordFocusNode.requestFocus();
  }

  override dispose(): void {
    this._keywordController.dispose();
    this._keywordFocusNode.dispose();
  }
}

export function _MusicPlaylistFilterDialog(props: _MusicPlaylistFilterDialogProps) {
  const state = useViewState(() => new _MusicPlaylistFilterDialogState(props), props);
  const route = useRoute<PersonalPlaylistFilterDraft>();
  // LayoutBuilder around the count grid.
  const gridRef = useRef<HTMLDivElement | null>(null);
  const gridSize = useElementSize(gridRef);
  const gap = 8.0;
  const maxWidth = gridSize?.width ?? Infinity;
  const columns = maxWidth < 330 ? 2 : 3;
  // `(maxWidth - gap * (columns - 1)) / columns`, resolved by CSS so the first
  // (unmeasured) frame already lays out like Flutter.
  const itemWidth = `calc((100% - ${gap * (columns - 1)}px) / ${columns})`;
  return (
    <DialogFrame
      title="筛选"
      icon={Icons.filter_alt_outlined}
      adaptiveActions={[
        new ResponsiveDialogAction({
          label: '重置',
          icon: Icons.restart_alt,
          onPressed: () => {
            state._keywordController.clear();
            state.setState(() => {
              state._countFilter = personalPlaylistCountAll;
            });
          },
        }),
        new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close() }),
        new ResponsiveDialogAction({
          label: '确认',
          icon: Icons.check,
          tone: ButtonTone.primary,
          onPressed: () =>
            route.close(
              new PersonalPlaylistFilterDraft({ keyword: state._keywordController.text.trim(), countFilter: state._countFilter }),
            ),
        }),
      ]}
    >
      <Column mainAxisSize="min" crossAxisAlignment="stretch">
        <_MusicPlaylistFieldLabel text="名称关键字" />
        <SizedBox height={8} />
        <Input
          controller={state._keywordController}
          focusNode={state._keywordFocusNode}
          hintText=""
          showClearButton
          minLines={1}
          maxLines={1}
        />
        <SizedBox height={16} />
        <_MusicPlaylistFieldLabel text="歌曲数量" />
        <SizedBox height={8} />
        <div ref={gridRef} style={{ display: 'flex', flexDirection: 'row', flexWrap: 'wrap', gap, minWidth: 0 }}>
          {_MusicPlaylistFilterDialogState._countFilters.map((filter) => {
            const selected = state._countFilter === filter.value;
            return (
              <SizedBox key={filter.value} width={itemWidth}>
                <PressableSurface
                  onPressed={() =>
                    state.setState(() => {
                      state._countFilter = filter.value;
                    })
                  }
                  selected={selected}
                  height={38}
                  backgroundColor={_primaryDark}
                  selectedBackgroundColor={UiColors.selected}
                  pressedBackgroundColor={_primaryDarkLow}
                  borderColor={_borderColor}
                  selectedBorderColor={UiColors.selectedBorder}
                >
                  <Center>
                    <Text
                      maxLines={1}
                      overflow="ellipsis"
                      style={{ color: selected ? _cyan : _textSecondary, fontSize: 13, fontWeight: 600, maxWidth: '100%' }}
                    >
                      {filter.label}
                    </Text>
                  </Center>
                </PressableSurface>
              </SizedBox>
            );
          })}
        </div>
      </Column>
    </DialogFrame>
  );
}

// ---------------------------------------------------------------------------
// 分享歌单 / 分享歌曲

export interface _MusicPlaylistShareRoomDialogProps {
  api: GangApi;
  playlistName: string;
  currentUser: CurrentUser | null;
  dialogTitle?: string;
  itemLabel?: string;
  keyPrefix?: string;
}

export class _MusicPlaylistShareRoomDialogState extends ViewState<_MusicPlaylistShareRoomDialogProps> {
  readonly _searchController = new TextEditingController();
  readonly _searchFocusNode = new FocusNode();
  _rooms: RoomCard[] = [];
  _selectedRoomId: string | null = null;
  _error: string | null = null;
  _loading = true;
  /** `Navigator.of(context)`; refreshed on every build. */
  _close: (room?: RoomCard) => void = () => {};

  override initState(): void {
    unawaited(this._loadRooms());
  }

  override dispose(): void {
    this._searchController.dispose();
    this._searchFocusNode.dispose();
  }

  async _loadRooms(): Promise<void> {
    this.setState(() => {
      this._loading = true;
      this._error = null;
    });
    try {
      const rooms: RoomCard[] = [];
      const seen = new Set<string>();
      const seenCursors = new Set<string>();
      let cursor: string | null = null;
      do {
        const page = await this.widget.api.listRooms({ limit: 50, cursor });
        for (const room of page.rooms) {
          if (!seen.has(room.id)) {
            seen.add(room.id);
            rooms.push(room);
          }
        }
        const next = page.nextCursor?.trim() ?? null;
        if (next == null || next.length === 0 || seenCursors.has(next)) {
          cursor = null;
        } else {
          seenCursors.add(next);
          cursor = next;
        }
      } while (cursor != null);
      if (!this.mounted) return;
      this.setState(() => {
        this._rooms = rooms;
        this._loading = false;
      });
      // PORT: `addPostFrameCallback` → the next animation frame.
      requestAnimationFrame(() => {
        if (this.mounted) this._searchFocusNode.requestFocus();
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._loading = false;
        this._error = String(error);
      });
    }
  }

  get _visibleRooms(): RoomCard[] {
    const query = this._searchController.text.trim().toLowerCase();
    if (query.length === 0) return this._rooms;
    return this._rooms.filter(
      (room) =>
        room.displayName.toLowerCase().includes(query) ||
        room.name.toLowerCase().includes(query) ||
        room.rid.toLowerCase().includes(query),
    );
  }

  _confirm = (): void => {
    let selected: RoomCard | null = null;
    for (const room of this._rooms) {
      if (room.id === this._selectedRoomId) {
        selected = room;
        break;
      }
    }
    if (selected != null) this._close(selected);
  };

  _roomProfile(room: RoomCard): PublicRoom {
    return new PublicRoom({
      id: room.id,
      rid: room.rid,
      name: room.displayName,
      avatarLabel: room.name,
      avatarUrl: room.avatarUrl,
      defaultAvatarKey: room.defaultAvatarKey,
      visibility: room.visibility,
      joinPolicy: 'approval_required',
      description: room.description,
      memberCount: room.memberCount,
      onlineMemberCount: room.onlineMemberCount,
      liveParticipantCount: room.liveParticipantCount,
      joined: true,
      joinState: 'joined',
    });
  }

  _resolveRoomProfile = async (room: PublicRoom): Promise<PublicRoom> => {
    const detail = await this.widget.api.getRoom(room.id);
    return publicRoomFromRoomDetail(detail);
  };
}

export function _MusicPlaylistShareRoomDialog(props: _MusicPlaylistShareRoomDialogProps) {
  const { dialogTitle = '分享歌单', itemLabel = '歌单', keyPrefix = 'music-playlist-share-room' } = props;
  const state = useViewState(() => new _MusicPlaylistShareRoomDialogState(), props);
  const route = useRoute<RoomCard>();
  state._close = route.close;
  const appConfig = useAppConfig();
  const windowHeight = _useWindowHeight();
  const visibleRooms = state._visibleRooms;
  const viewportHeight = _clamp(windowHeight * 0.58, 240.0, 560.0);
  let list: ReactNode;
  if (state._loading) {
    list = <_CenteredProgress />;
  } else if (visibleRooms.length === 0) {
    list = <_CenteredEmptyState text={state._rooms.length === 0 ? '还没有可分享的房间' : '没有匹配的房间'} />;
  } else {
    list = (
      <div style={{ flex: '1 1 0', minHeight: 0, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
        {visibleRooms.map((room) => {
          const selected = room.id === state._selectedRoomId;
          const avatar = (
            <Avatar
              label={room.displayName}
              imageUrl={appConfig.resolveAssetUrl(room.avatarUrl)}
              defaultAvatarKey={room.defaultAvatarKey}
              size={36}
            />
          );
          const currentUser = props.currentUser;
          return (
            <div key={room.id} style={{ flexShrink: 0, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
              <_ManagementCardTapTarget
                data-testid={`${keyPrefix}-option-${room.id}`}
                onTap={() =>
                  state.setState(() => {
                    state._selectedRoomId = room.id;
                  })
                }
              >
                <_SettingsSubPanel highlighted={selected} hoverable>
                  <Row>
                    {currentUser != null ? (
                      <RoomHoverCard
                        key={`${keyPrefix}-profile-${room.id}`}
                        room={state._roomProfile(room)}
                        currentUser={currentUser}
                        onResolveRoom={state._resolveRoomProfile}
                      >
                        {avatar}
                      </RoomHoverCard>
                    ) : (
                      avatar
                    )}
                    <SizedBox width={10} />
                    <Expanded>
                      <div style={{ paddingTop: 4, paddingBottom: 4, minWidth: 0, boxSizing: 'border-box' }}>
                        <Row>
                          <Expanded>
                            <Column crossAxisAlignment="start">
                              <Text style={{ color: _textPrimary, fontSize: 14, fontWeight: 600, maxWidth: '100%' }}>{room.displayName}</Text>
                              {room.rid.trim().length > 0 ? (
                                <>
                                  <SizedBox height={2} />
                                  <Text style={{ color: _textMuted, fontSize: 12, maxWidth: '100%' }}>{`RID：${room.rid}`}</Text>
                                </>
                              ) : null}
                            </Column>
                          </Expanded>
                          <Icon
                            icon={selected ? Icons.radio_button_checked : Icons.radio_button_off}
                            color={selected ? _cyan : _textMuted}
                            size={20}
                          />
                        </Row>
                      </div>
                    </Expanded>
                  </Row>
                </_SettingsSubPanel>
              </_ManagementCardTapTarget>
            </div>
          );
        })}
      </div>
    );
  }
  return (
    <DialogFrame
      title={dialogTitle}
      icon={Icons.share_outlined}
      maxWidth={620}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close() }),
        new ResponsiveDialogAction({
          label: '确认分享',
          icon: Icons.send_outlined,
          tone: ButtonTone.primary,
          onPressed: state._selectedRoomId == null ? null : state._confirm,
        }),
      ]}
    >
      <SizedBox height={viewportHeight} style={{ display: 'flex', flexDirection: 'column' }}>
        <Column crossAxisAlignment="stretch" style={{ flex: '1 1 0', minHeight: 0 }}>
          <Text style={{ color: _textMuted, fontSize: 12 }}>{`选择要将${itemLabel}“${props.playlistName}”分享到的文字频道`}</Text>
          <SizedBox height={10} />
          <Input
            controller={state._searchController}
            focusNode={state._searchFocusNode}
            prefixIcon={Icons.search}
            hintText="搜索房间"
            showClearButton
            onChanged={() => state.setState()}
            textInputAction={TextInputAction.search}
          />
          {state._error != null ? (
            <>
              <SizedBox height={8} />
              <_MusicPlaylistErrorText message={state._error} />
            </>
          ) : null}
          <SizedBox height={10} />
          <_ExpandedFill>{list}</_ExpandedFill>
        </Column>
      </SizedBox>
    </DialogFrame>
  );
}

// ---------------------------------------------------------------------------
// 新建 / 重命名 / 合并歌单

export interface _MusicPlaylistNameDialogProps {
  title: string;
  icon: IconData;
  confirmLabel: string;
  confirmIcon: IconData;
  initialName?: string;
  loadImportPlaylists?: (() => Promise<PersonalMusicPlaylistPage | null>) | null;
  description?: string | null;
}

export class _MusicPlaylistNameDialogState extends ViewState<_MusicPlaylistNameDialogProps> {
  constructor(props: _MusicPlaylistNameDialogProps) {
    super();
    this._controller = new TextEditingController({ text: props.initialName ?? '' });
  }

  readonly _controller: TextEditingController;
  readonly _focusNode = new FocusNode();
  _importPlaylist: PersonalMusicPlaylist | null = null;
  _loadingImportPlaylists = false;
  _error: string | null = null;
  /** `Navigator.of(context)`; refreshed on every build. */
  _close: (draft?: PersonalPlaylistCreateDraft) => void = () => {};

  override initState(): void {
    // PORT: `addPostFrameCallback` → the next animation frame, after the
    // field has attached to its FocusNode.
    requestAnimationFrame(() => {
      if (!this.mounted) return;
      this._focusNode.requestFocus();
      this._controller.selection = new TextSelection({ baseOffset: 0, extentOffset: this._controller.text.length });
    });
  }

  override dispose(): void {
    this._controller.dispose();
    this._focusNode.dispose();
  }

  _submit = (): void => {
    const name = normalizedPersonalPlaylistName(this._controller.text);
    if (name == null) {
      this.setState(() => {
        this._error = '请输入 1～64 个字符的歌单名';
      });
      return;
    }
    this._close(new PersonalPlaylistCreateDraft({ name, importPlaylistId: this._importPlaylist?.id ?? null }));
  };

  _chooseImportPlaylist = async (): Promise<void> => {
    const load = this.widget.loadImportPlaylists;
    if (load == null || this._loadingImportPlaylists) return;
    // FocusScope.of(context).unfocus()
    const active = document.activeElement;
    if (active instanceof HTMLElement) active.blur();
    this.setState(() => {
      this._loadingImportPlaylists = true;
      this._error = null;
    });
    try {
      const page = await load();
      if (!this.mounted) return;
      const selected = await showDialog<PersonalMusicPlaylist>(() => (
        <_MusicPlaylistImportDialog playlists={page?.playlists ?? []} selectedPlaylistId={this._importPlaylist?.id ?? null} />
      ));
      if (!this.mounted || selected == null) return;
      this.setState(() => {
        this._importPlaylist = selected;
      });
    } catch (error) {
      if (this.mounted) {
        this.setState(() => {
          this._error = String(error);
        });
      }
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._loadingImportPlaylists = false;
        });
      }
    }
  };
}

export function _MusicPlaylistNameDialog(props: _MusicPlaylistNameDialogProps) {
  const state = useViewState(() => new _MusicPlaylistNameDialogState(props), props);
  const route = useRoute<PersonalPlaylistCreateDraft>();
  state._close = route.close;
  const { initialName = '' } = props;
  const importPlaylist = state._importPlaylist;
  return (
    <DialogFrame
      title={props.title}
      icon={props.icon}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close() }),
        new ResponsiveDialogAction({ label: props.confirmLabel, icon: props.confirmIcon, tone: ButtonTone.primary, onPressed: state._submit }),
      ]}
    >
      <Column mainAxisSize="min" crossAxisAlignment="stretch">
        {props.description != null ? (
          <>
            <Text style={{ color: _textMuted, fontSize: 12, lineHeight: 1.5 }}>{props.description}</Text>
            <SizedBox height={10} />
          </>
        ) : null}
        <Input
          controller={state._controller}
          focusNode={state._focusNode}
          hintText={initialName.length === 0 ? '歌单名称' : ''}
          textInputAction={TextInputAction.done}
          onSubmitted={() => state._submit()}
        />
        {props.loadImportPlaylists != null ? (
          <>
            <SizedBox height={10} />
            <Button
              data-testid="import-personal-music-playlist-button"
              width={Infinity}
              loading={state._loadingImportPlaylists}
              selected={importPlaylist != null}
              tone={importPlaylist == null ? ButtonTone.neutral : ButtonTone.primary}
              icon={Icons.library_add_outlined}
              onPressed={() => unawaited(state._chooseImportPlaylist())}
            >
              导入我的歌单
            </Button>
            {importPlaylist != null ? (
              <>
                <SizedBox height={8} />
                <div
                  data-testid="selected-personal-music-playlist-import"
                  style={{
                    boxSizing: 'border-box',
                    padding: '8px 6px 8px 12px',
                    backgroundColor: _primaryDark,
                    border: `1px solid ${UiColors.selectedBorder}`,
                    borderRadius: 8,
                  }}
                >
                  <Row>
                    <Icon icon={Icons.queue_music} color={_cyan} size={20} />
                    <SizedBox width={10} />
                    <Expanded>
                      <Column crossAxisAlignment="start">
                        <Text style={{ color: _textPrimary, fontSize: 13, fontWeight: 600, maxWidth: '100%' }}>{importPlaylist.name}</Text>
                        <SizedBox height={2} />
                        <Text style={{ color: _textMuted, fontSize: 12, maxWidth: '100%' }}>{`${importPlaylist.itemCount} 首歌曲`}</Text>
                      </Column>
                    </Expanded>
                    <ButtonIcon
                      data-testid="clear-personal-music-playlist-import"
                      icon={Icons.close}
                      tooltip="取消导入"
                      onPressed={() =>
                        state.setState(() => {
                          state._importPlaylist = null;
                        })
                      }
                      size={32}
                    />
                  </Row>
                </div>
              </>
            ) : null}
          </>
        ) : null}
        {state._error != null ? (
          <>
            <SizedBox height={8} />
            <_MusicPlaylistErrorText message={state._error} />
          </>
        ) : null}
      </Column>
    </DialogFrame>
  );
}

// ---------------------------------------------------------------------------
// 选择歌单

export interface _MusicPlaylistImportDialogProps {
  playlists: readonly PersonalMusicPlaylist[];
  selectedPlaylistId: string | null;
  title?: string;
  emptyText?: string;
  /** Dart `pickerKey`, exposed as a test id. */
  pickerKey?: string;
  optionKeyPrefix?: string;
}

export function _MusicPlaylistImportDialog({
  playlists,
  selectedPlaylistId,
  title = '选择歌单',
  emptyText = '我的歌单为空',
  pickerKey = 'personal-music-playlist-import-picker',
  optionKeyPrefix = 'personal-music-playlist-import-option',
}: _MusicPlaylistImportDialogProps) {
  const route = useRoute<PersonalMusicPlaylist>();
  const windowHeight = _useWindowHeight();
  const viewportHeight = _clamp(windowHeight * 0.56, 180.0, 520.0);
  return (
    <DialogFrame
      title={title}
      icon={Icons.library_music_outlined}
      maxWidth={560}
      adaptiveActions={[new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close() })]}
    >
      <SizedBox data-testid={pickerKey} height={viewportHeight} style={{ display: 'flex', flexDirection: 'column' }}>
        {playlists.length === 0 ? (
          <Center style={{ flex: '1 1 0', minHeight: 0, flexDirection: 'column', alignItems: 'stretch' }}>
            <_SettingsEmptyState text={emptyText} />
          </Center>
        ) : (
          <div style={{ flex: '1 1 0', minHeight: 0, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 10 }}>
            {playlists.map((playlist) => (
              <div key={playlist.id} style={{ flexShrink: 0, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                <_MusicPlaylistImportTile
                  playlist={playlist}
                  selected={playlist.id === selectedPlaylistId}
                  optionKeyPrefix={optionKeyPrefix}
                  onPressed={() => route.close(playlist)}
                />
              </div>
            ))}
          </div>
        )}
      </SizedBox>
    </DialogFrame>
  );
}

export interface _MusicPlaylistImportTileProps {
  playlist: PersonalMusicPlaylist;
  selected: boolean;
  optionKeyPrefix?: string;
  onPressed: () => void;
}

const _importTileNameFontSize = 13;
const _importTileNameLineHeight = 1.25;

export function _MusicPlaylistImportTile({
  playlist,
  selected,
  optionKeyPrefix = 'personal-music-playlist-import-option',
  onPressed,
}: _MusicPlaylistImportTileProps) {
  // LayoutBuilder: the tile's width decides how many lines the name wraps to.
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  const nameStyle = {
    color: _textPrimary,
    fontSize: _importTileNameFontSize,
    fontWeight: 600,
    lineHeight: _importTileNameLineHeight,
  } as const;
  // PORT: `TextPainter.layout(maxWidth:)` → canvas line wrapping × line height.
  const maxWidth = Math.max(40.0, (size?.width ?? Infinity) - 118);
  const lines = measureLineCount(playlist.name, nameStyle, maxWidth);
  const measuredHeight = lines * _importTileNameFontSize * _importTileNameLineHeight + 39;
  const height = measuredHeight < 58 ? 58.0 : measuredHeight;
  return (
    <div ref={ref} style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
      <PressableSurface
        data-testid={`${optionKeyPrefix}-${playlist.id}`}
        height={height}
        selected={selected}
        onPressed={onPressed}
        padding={{ horizontal: 14, vertical: 8 }}
      >
        <Row>
          <Icon icon={Icons.queue_music} color={_cyan} size={22} />
          <SizedBox width={12} />
          <Expanded>
            <Text style={nameStyle}>{playlist.name}</Text>
          </Expanded>
          <SizedBox width={10} />
          <Text softWrap={false} style={{ color: _textMuted, fontSize: 12 }}>{`${playlist.itemCount} 首`}</Text>
          <SizedBox width={6} />
          <Icon icon={selected ? Icons.check : Icons.chevron_right} color={selected ? _cyan : _textMuted} size={20} />
        </Row>
      </PressableSurface>
    </div>
  );
}
