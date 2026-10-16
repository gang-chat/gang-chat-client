import { useEffect, useState, useSyncExternalStore, type ReactNode } from 'react';

import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import * as music_box_display from '@gang-chat/core/app/music_box_display';
import {
  MusicTrackPreviewTrack,
  type MusicTrackPreviewController,
  type MusicTrackPreviewSnapshot,
} from '@gang-chat/core/app/music_track_preview';
import { unawaited } from '@gang-chat/core/lib/async';
import type { PersonalMusicPlaylist } from '@gang-chat/core/protocol/models';

import { Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { FocusNode, TextEditingController, TextInputAction } from '../lib/text_editing';
import { useViewState, ViewState } from '../lib/view_state';
import { ButtonTone } from '../ui/button';
import {
  DialogFrame,
  ResponsiveDialogAction,
  ResponsiveDialogActionBar,
  showFloatingErrorNotice,
  showFloatingSuccessNotice,
} from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { Input } from '../ui/input';
import { showDialog, useRoute } from '../ui/overlay';
import { Segment, SegmentedControl } from '../ui/segmented_control';
import { UiColors, UiCurves, UiRadii, UiSpacing, UiTypography } from '../ui/tokens';
import { HoverCardAnchor, HoverCardSelectableText } from './hover_card_anchor';

export class MusicTrackCardData {
  constructor(opts: {
    id: string;
    source: string;
    trackId: string;
    title: string;
    artists: readonly string[];
    durationMs: number;
  }) {
    this.id = opts.id;
    this.source = opts.source;
    this.trackId = opts.trackId;
    this.title = opts.title;
    this.artists = opts.artists;
    this.durationMs = opts.durationMs;
  }

  readonly id: string;
  readonly source: string;
  readonly trackId: string;
  readonly title: string;
  readonly artists: readonly string[];
  readonly durationMs: number;

  get previewTrack(): MusicTrackPreviewTrack {
    return new MusicTrackPreviewTrack({ source: this.source, trackId: this.trackId });
  }
}

export enum MusicTrackPlaylistTargetScope {
  personal = 'personal',
  room = 'room',
}

/**
 * A writable playlist shown by a song card.
 *
 * Keeping the scope beside the playlist prevents room playlists from being
 * accidentally submitted through the personal-playlist API when a picker
 * contains both kinds of target.
 */
export class MusicTrackPlaylistTarget {
  private constructor(playlist: PersonalMusicPlaylist, scope: MusicTrackPlaylistTargetScope, roomId: string | null) {
    this.playlist = playlist;
    this.scope = scope;
    this.roomId = roomId;
  }

  /** `MusicTrackPlaylistTarget.personal(playlist)`; usable as a `map` callback. */
  static personal(playlist: PersonalMusicPlaylist): MusicTrackPlaylistTarget {
    return new MusicTrackPlaylistTarget(playlist, MusicTrackPlaylistTargetScope.personal, null);
  }

  /** `MusicTrackPlaylistTarget.room(playlist:, roomId:)`. */
  static room(opts: { playlist: PersonalMusicPlaylist; roomId: string }): MusicTrackPlaylistTarget {
    return new MusicTrackPlaylistTarget(opts.playlist, MusicTrackPlaylistTargetScope.room, opts.roomId);
  }

  readonly playlist: PersonalMusicPlaylist;
  readonly scope: MusicTrackPlaylistTargetScope;
  readonly roomId: string | null;

  get roomScoped(): boolean {
    return this.scope === MusicTrackPlaylistTargetScope.room;
  }

  get key(): string {
    return `${this.scope}:${this.roomId ?? ''}:${this.playlist.id}`;
  }
}

export interface MusicTrackHoverCardProps {
  data: MusicTrackCardData;
  previewController: MusicTrackPreviewController;
  children?: ReactNode;
  playlists?: readonly PersonalMusicPlaylist[];
  loadPlaylists?: (() => Promise<PersonalMusicPlaylist[]>) | null;
  playlistsRoomScoped?: boolean;
  onAddToPlaylist?: ((playlist: PersonalMusicPlaylist) => Promise<void>) | null;
  playlistTargets?: readonly MusicTrackPlaylistTarget[];
  loadPlaylistTargets?: (() => Promise<MusicTrackPlaylistTarget[]>) | null;
  playlistTargetsLoadKey?: unknown;
  onAddToPlaylistTarget?: ((target: MusicTrackPlaylistTarget) => Promise<void>) | null;
}

/**
 * Song profile card for saved-playlist rows. Preview is local-only: it never
 * changes the room queue or authoritative music-box playback state.
 */
class _MusicTrackHoverCardState extends ViewState<MusicTrackHoverCardProps> {
  override didUpdateProps(oldWidget: MusicTrackHoverCardProps): void {
    if (
      oldWidget.data.previewTrack.key !== this.widget.data.previewTrack.key ||
      oldWidget.previewController !== this.widget.previewController
    ) {
      unawaited(oldWidget.previewController.stopIf(oldWidget.data.previewTrack.key));
    }
  }

  _loadPlaylistTargets = async (): Promise<MusicTrackPlaylistTarget[]> => {
    const loadTargets = this.widget.loadPlaylistTargets;
    const loadLegacy = this.widget.loadPlaylists;
    if (loadTargets != null) return loadTargets();
    if (loadLegacy != null) {
      return (await loadLegacy()).map((playlist) => this._legacyPlaylistTarget(playlist));
    }
    const playlistTargets = this.widget.playlistTargets ?? [];
    if (playlistTargets.length > 0) return [...playlistTargets];
    return (this.widget.playlists ?? []).map((playlist) => this._legacyPlaylistTarget(playlist));
  };

  _legacyPlaylistTarget(playlist: PersonalMusicPlaylist): MusicTrackPlaylistTarget {
    return this.widget.playlistsRoomScoped === true
      ? MusicTrackPlaylistTarget.room({ playlist, roomId: '' })
      : MusicTrackPlaylistTarget.personal(playlist);
  }

  override dispose(): void {
    unawaited(this.widget.previewController.stopIf(this.widget.data.previewTrack.key));
  }

  _togglePreview = async (): Promise<void> => {
    try {
      await this.widget.previewController.toggle(this.widget.data.previewTrack);
    } catch (error) {
      if (this.mounted) {
        showFloatingErrorNotice(userFacingErrorMessage(error, { fallback: '试听失败，请重试' }));
      }
    }
  };

  _addToPlaylist = async (target: MusicTrackPlaylistTarget): Promise<void> => {
    const addTarget = this.widget.onAddToPlaylistTarget;
    const addLegacy = this.widget.onAddToPlaylist;
    if (addTarget != null) {
      await addTarget(target);
    } else if (addLegacy != null) {
      await addLegacy(target.playlist);
    }
  };

  _openPlaylistPicker = async (): Promise<void> => {
    const target = await showMusicTrackPlaylistTargetDialog({
      loadTargets: this._loadPlaylistTargets,
      onAdd: this._addToPlaylist,
    });
    if (!this.mounted || target == null) return;
    showFloatingSuccessNotice(`已添加到「${target.playlist.name}」`);
  };

  build(): ReactNode {
    const widget = this.widget;
    const trackKey = widget.data.previewTrack.key;
    return (
      <HoverCardAnchor
        cardWidth={304}
        resetKey={JSON.stringify([widget.data.id, trackKey])}
        onVisibilityChanged={(visible: boolean) => {
          if (!visible) {
            unawaited(widget.previewController.stopIf(trackKey));
          }
        }}
        cardBuilder={() => (
          <_MusicTrackPreviewBuilder controller={widget.previewController}>
            {(preview) => (
              <_MusicTrackProfileCard
                data={widget.data}
                loading={preview.isLoading(trackKey)}
                playing={preview.isPlaying(trackKey)}
                onTogglePreview={() => void this._togglePreview()}
                onOpenPlaylistPicker={
                  widget.onAddToPlaylist == null && widget.onAddToPlaylistTarget == null
                    ? null
                    : () => unawaited(this._openPlaylistPicker())
                }
              />
            )}
          </_MusicTrackPreviewBuilder>
        )}
      >
        {widget.children}
      </HoverCardAnchor>
    );
  }
}

export function MusicTrackHoverCard(props: MusicTrackHoverCardProps) {
  const state = useViewState(() => new _MusicTrackHoverCardState(), props);
  return <>{state.build()}</>;
}

/**
 * `StreamBuilder<MusicTrackPreviewSnapshot>(stream: controller.changes,
 * initialData: controller.snapshot)`.
 */
function _MusicTrackPreviewBuilder({
  controller,
  children,
}: {
  controller: MusicTrackPreviewController;
  children: (preview: MusicTrackPreviewSnapshot) => ReactNode;
}) {
  const [snapshot, setSnapshot] = useState<{ controller: MusicTrackPreviewController; data: MusicTrackPreviewSnapshot }>(
    () => ({ controller, data: controller.snapshot }),
  );
  useEffect(() => {
    setSnapshot({ controller, data: controller.snapshot });
    const subscription = controller.changes.listen((next) => setSnapshot({ controller, data: next }));
    return () => subscription.cancel();
  }, [controller]);
  const preview = snapshot.controller === controller ? snapshot.data : controller.snapshot;
  return <>{children(preview)}</>;
}

function _MusicTrackProfileCard({
  data,
  loading,
  playing,
  onTogglePreview,
  onOpenPlaylistPicker,
}: {
  data: MusicTrackCardData;
  loading: boolean;
  playing: boolean;
  onTogglePreview: () => void;
  onOpenPlaylistPicker: (() => void) | null;
}) {
  const artists = data.artists
    .map((value) => value.trim())
    .filter((value) => value.length > 0)
    .join('、');
  const isBilibili = data.source.trim().toLowerCase() === 'bilibili';
  const actions: ResponsiveDialogAction[] = [
    new ResponsiveDialogAction({
      label: playing ? '取消试听' : '试听',
      buttonKey: 'music-track-card-preview',
      icon: playing ? Icons.stop_rounded : Icons.play_arrow,
      tone: playing ? ButtonTone.danger : ButtonTone.primary,
      loading,
      onPressed: loading ? null : onTogglePreview,
    }),
  ];
  if (onOpenPlaylistPicker != null) {
    actions.push(
      new ResponsiveDialogAction({
        label: '添加到歌单',
        buttonKey: 'music-track-card-add-to-playlist',
        icon: Icons.playlist_add,
        tone: ButtonTone.primary,
        onPressed: onOpenPlaylistPicker,
      }),
    );
  }
  return (
    <Column
      data-testid={`music-track-card:${data.id}`}
      mainAxisSize="min"
      crossAxisAlignment="stretch"
      style={{ padding: UiSpacing.lg, boxSizing: 'border-box' }}
    >
      <Row crossAxisAlignment="start">
        <div style={{ paddingTop: 2, flexShrink: 0 }}>
          <Icon icon={Icons.music_note} color={UiColors.accent} size={24} />
        </div>
        <SizedBox width={UiSpacing.sm} />
        <Expanded>
          <HoverCardSelectableText value={data.title} maxLines={12} style={{ ...UiTypography.title, fontSize: 16 }} />
        </Expanded>
      </Row>
      <SizedBox height={UiSpacing.md} />
      <_MusicTrackDetailRow
        label="时长"
        value={data.durationMs > 0 ? music_box_display.musicBoxFormatDuration(data.durationMs) : '未知'}
      />
      <_MusicTrackDetailRow
        label={music_box_display.musicBoxArtistFieldLabel(data.source)}
        value={artists.length === 0 ? (isBilibili ? '未知作者' : '未知歌手') : artists}
      />
      <_MusicTrackDetailRow label="来源" value={music_box_display.musicBoxSourceLabel(data.source)} />
      {isBilibili ? <_MusicTrackDetailRow label="详情" value={_bilibiliTrackID(data.trackId)} /> : null}
      <SizedBox height={UiSpacing.md} />
      <ResponsiveDialogActionBar expanded actions={actions} />
    </Column>
  );
}

enum _MusicTrackPlaylistTargetFilter {
  all = 'all',
  personal = 'personal',
  room = 'room',
}

/**
 * Port of `showMusicTrackPlaylistTargetDialog(context, …)`; the
 * `BuildContext` argument is dropped. Resolves with the target the track was
 * added to, or null when the dialog was dismissed.
 */
export function showMusicTrackPlaylistTargetDialog(opts: {
  loadTargets: () => Promise<MusicTrackPlaylistTarget[]>;
  onAdd: (target: MusicTrackPlaylistTarget) => Promise<void>;
}): Promise<MusicTrackPlaylistTarget | null> {
  return showDialog<MusicTrackPlaylistTarget>(() => (
    <_MusicTrackPlaylistTargetDialog loadTargets={opts.loadTargets} onAdd={opts.onAdd} />
  )).then((value) => value ?? null);
}

interface _MusicTrackPlaylistTargetDialogProps {
  loadTargets: () => Promise<MusicTrackPlaylistTarget[]>;
  onAdd: (target: MusicTrackPlaylistTarget) => Promise<void>;
  /** Navigator.pop for the dialog route. */
  close: (result?: MusicTrackPlaylistTarget) => void;
  windowHeight: number;
}

class _MusicTrackPlaylistTargetDialogState extends ViewState<_MusicTrackPlaylistTargetDialogProps> {
  readonly _searchController = new TextEditingController();
  readonly _searchFocusNode = new FocusNode();
  _targets: readonly MusicTrackPlaylistTarget[] = [];
  _filter: _MusicTrackPlaylistTargetFilter = _MusicTrackPlaylistTargetFilter.all;
  _selectedKey: string | null = null;
  _error: string | null = null;
  _loading = true;
  _adding = false;

  override initState(): void {
    unawaited(this._load());
  }

  override dispose(): void {
    this._searchController.dispose();
    this._searchFocusNode.dispose();
  }

  async _load(): Promise<void> {
    try {
      const targets = await this.widget.loadTargets();
      if (!this.mounted) return;
      this.setState(() => {
        this._targets = targets;
        this._loading = false;
      });
      // addPostFrameCallback: focus once the list frame is committed.
      setTimeout(() => {
        if (this.mounted) this._searchFocusNode.requestFocus();
      }, 0);
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._loading = false;
        this._error = userFacingErrorMessage(error, { fallback: '加载歌单失败，请重试' });
      });
    }
  }

  get _visibleTargets(): MusicTrackPlaylistTarget[] {
    const query = this._searchController.text.trim().toLowerCase();
    return this._targets.filter((target) => {
      let matchesScope: boolean;
      switch (this._filter) {
        case _MusicTrackPlaylistTargetFilter.all:
          matchesScope = true;
          break;
        case _MusicTrackPlaylistTargetFilter.personal:
          matchesScope = !target.roomScoped;
          break;
        case _MusicTrackPlaylistTargetFilter.room:
          matchesScope = target.roomScoped;
          break;
      }
      return matchesScope && (query.length === 0 || target.playlist.name.toLowerCase().includes(query));
    });
  }

  get _selectedTarget(): MusicTrackPlaylistTarget | null {
    for (const target of this._targets) {
      if (target.key === this._selectedKey) return target;
    }
    return null;
  }

  _confirm = async (): Promise<void> => {
    const target = this._selectedTarget;
    if (target == null || this._adding) return;
    this.setState(() => {
      this._adding = true;
    });
    try {
      await this.widget.onAdd(target);
      if (this.mounted) this.widget.close(target);
    } catch (error) {
      if (this.mounted) {
        showFloatingErrorNotice(userFacingErrorMessage(error, { fallback: '添加失败，请检查歌单权限' }));
      }
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._adding = false;
        });
      }
    }
  };

  build(): ReactNode {
    const visibleTargets = this._visibleTargets;
    const height = Math.min(Math.max(this.widget.windowHeight * 0.58, 240), 560);
    let body: ReactNode;
    if (this._loading) {
      body = (
        <div style={{ flex: '1 1 0', minHeight: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <CircularProgressIndicator />
        </div>
      );
    } else if (visibleTargets.length === 0) {
      body = (
        <div style={{ flex: '1 1 0', minHeight: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Text style={{ ...UiTypography.body, color: UiColors.textMuted }}>
            {this._targets.length === 0 ? '还没有可用歌单' : '没有匹配的歌单'}
          </Text>
        </div>
      );
    } else {
      // ListView with 10px of right padding.
      // PORT: Flutter drew a RawScrollbar (7px, pill, textMuted 82%); the list
      // uses the app's global thin scrollbar (ui.css) instead.
      body = (
        <div
          style={{
            flex: '1 1 0',
            minHeight: 0,
            overflowY: 'auto',
            paddingRight: 10,
            boxSizing: 'border-box',
            display: 'flex',
            flexDirection: 'column',
            gap: UiSpacing.sm,
          }}
        >
          {visibleTargets.map((target) => (
            <_MusicTrackPlaylistTargetOption
              key={target.key}
              target={target}
              selected={target.key === this._selectedKey}
              onPressed={
                this._adding
                  ? null
                  : () =>
                      this.setState(() => {
                        this._selectedKey = target.key;
                      })
              }
            />
          ))}
        </div>
      );
    }
    return (
      <DialogFrame
        title="添加到歌单"
        icon={Icons.playlist_add}
        maxWidth={620}
        adaptiveActions={[
          new ResponsiveDialogAction({
            label: '取消',
            onPressed: this._adding ? null : () => this.widget.close(),
          }),
          new ResponsiveDialogAction({
            buttonKey: 'music-track-playlist-target-confirm',
            label: '确认添加',
            icon: Icons.playlist_add,
            tone: ButtonTone.primary,
            loading: this._adding,
            onPressed: this._selectedTarget == null || this._adding ? null : () => void this._confirm(),
          }),
        ]}
      >
        <Column crossAxisAlignment="stretch" style={{ height, flexShrink: 1, minHeight: 0 }}>
          <div data-testid="music-track-playlist-target-search">
            <Input
              controller={this._searchController}
              focusNode={this._searchFocusNode}
              prefixIcon={Icons.search}
              hintText="搜索歌单"
              showClearButton
              onChanged={() => this.setState()}
              textInputAction={TextInputAction.search}
            />
          </div>
          <SizedBox height={UiSpacing.sm} />
          <SegmentedControl<_MusicTrackPlaylistTargetFilter>
            expanded
            value={this._filter}
            onChanged={(value) =>
              this.setState(() => {
                this._filter = value;
              })
            }
            segments={[
              new Segment({ value: _MusicTrackPlaylistTargetFilter.all, label: '全部' }),
              new Segment({ value: _MusicTrackPlaylistTargetFilter.personal, label: '我的歌单' }),
              new Segment({ value: _MusicTrackPlaylistTargetFilter.room, label: '房间歌单' }),
            ]}
          />
          {this._error != null ? (
            <>
              <SizedBox height={UiSpacing.sm} />
              <Text style={{ ...UiTypography.label, color: UiColors.danger }}>{this._error}</Text>
            </>
          ) : null}
          <SizedBox height={UiSpacing.sm} />
          {body}
        </Column>
      </DialogFrame>
    );
  }
}

function _subscribeWindowResize(onChange: () => void): () => void {
  window.addEventListener('resize', onChange);
  return () => window.removeEventListener('resize', onChange);
}

function _windowHeight(): number {
  return window.innerHeight;
}

/** `MediaQuery.sizeOf(context).height` as a hook. */
export function _useWindowHeight(): number {
  return useSyncExternalStore(_subscribeWindowResize, _windowHeight, _windowHeight);
}

function _MusicTrackPlaylistTargetDialog(props: {
  loadTargets: () => Promise<MusicTrackPlaylistTarget[]>;
  onAdd: (target: MusicTrackPlaylistTarget) => Promise<void>;
}) {
  const route = useRoute<MusicTrackPlaylistTarget>();
  const windowHeight = _useWindowHeight();
  const state = useViewState(() => new _MusicTrackPlaylistTargetDialogState(), {
    ...props,
    close: route.close,
    windowHeight,
  });
  return <>{state.build()}</>;
}

function _MusicTrackPlaylistTargetOption({
  target,
  selected,
  onPressed,
}: {
  target: MusicTrackPlaylistTarget;
  selected: boolean;
  onPressed: (() => void) | null;
}) {
  return (
    <div
      role="button"
      aria-pressed={selected}
      aria-disabled={onPressed == null}
      data-testid={`music-track-playlist-target:${target.roomScoped ? target.key : target.playlist.id}`}
      onClick={(event) => {
        if (event.button !== 0) return;
        onPressed?.();
      }}
      style={{
        flexShrink: 0,
        minHeight: 64,
        padding: 14,
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: selected ? UiColors.selected : UiColors.surfaceLow,
        borderRadius: UiRadii.md,
        border: `1px solid ${selected ? UiColors.selectedBorder : UiColors.border}`,
        transition: `background-color 90ms ${UiCurves.linear}, border-color 90ms ${UiCurves.linear}`,
        cursor: SystemMouseCursors.basic,
      }}
    >
      <Icon icon={target.roomScoped ? Icons.meeting_room : Icons.person} color={UiColors.accent} size={20} />
      <SizedBox width={UiSpacing.md} />
      <Expanded>
        <Column mainAxisSize="min" crossAxisAlignment="start">
          <Text style={{ ...UiTypography.body, color: UiColors.text, fontWeight: 600 }}>{target.playlist.name}</Text>
          <SizedBox height={2} />
          <Text style={{ ...UiTypography.label, color: UiColors.textMuted }}>{`${target.playlist.itemCount} 首歌曲`}</Text>
        </Column>
      </Expanded>
      <SizedBox width={UiSpacing.md} />
      <Icon
        icon={selected ? Icons.radio_button_checked : Icons.radio_button_off}
        color={selected ? UiColors.accent : UiColors.textMuted}
        size={20}
      />
    </div>
  );
}

function _MusicTrackDetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ paddingBottom: 6 }}>
      <Row crossAxisAlignment="start">
        <SizedBox width={38}>
          <HoverCardSelectableText value={label} style={{ ...UiTypography.label, color: UiColors.textMuted }} />
        </SizedBox>
        <Expanded>
          <HoverCardSelectableText
            value={value}
            textAlign="right"
            maxLines={12}
            style={{ ...UiTypography.label, color: UiColors.text }}
          />
        </Expanded>
      </Row>
    </div>
  );
}

const _bvidPattern = /BV[0-9A-Za-z]+/i;

function _bilibiliTrackID(trackId: string): string {
  const raw = trackId.trim();
  const match = _bvidPattern.exec(raw)?.[0] ?? null;
  if (match == null || match.length <= 2) return raw.length === 0 ? '未知' : raw;
  return `BV${match.substring(2)}`;
}
