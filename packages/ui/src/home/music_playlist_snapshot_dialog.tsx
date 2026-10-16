import { useRef, type CSSProperties, type ReactNode } from 'react';

import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import * as music_box_display from '@gang-chat/core/app/music_box_display';
import {
  MusicTrackPreviewController,
  type MusicTrackPreviewPlatformFactory,
} from '@gang-chat/core/app/music_track_preview';
import { unawaited } from '@gang-chat/core/lib/async';
import type { MusicTrackPreviewApi } from '@gang-chat/core/protocol/api_client';
import type { PersonalMusicPlaylist, PersonalMusicPlaylistPage } from '@gang-chat/core/protocol/models';

import { Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { measureLineCount } from '../lib/text_measure';
import { useElementSize } from '../lib/use_element_size';
import { useViewState, ViewState } from '../lib/view_state';
import { ButtonTone } from '../ui/button';
import { DialogFrame, ResponsiveDialogAction, showFloatingErrorNotice, showFloatingSuccessNotice } from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { MusicPlaylistTrackSurface } from '../ui/music_playlist_track_surface';
import { useRoute } from '../ui/overlay';
import { UiColors, UiTypography, kClientFontStack } from '../ui/tokens';
import { MusicTrackHoverCard, _useWindowHeight, type MusicTrackCardData } from './music_track_profile_card';

export type MusicPlaylistSnapshotLoader = () => Promise<PersonalMusicPlaylistPage>;
export type MusicPlaylistSnapshotAddTrack = (track: MusicTrackCardData, playlist: PersonalMusicPlaylist) => Promise<void>;
export type MusicPlaylistSnapshotClone = () => Promise<PersonalMusicPlaylist>;

export interface MusicPlaylistSnapshotDialogProps {
  title: string;
  tracks: readonly MusicTrackCardData[];
  loadPersonalPlaylists: MusicPlaylistSnapshotLoader;
  onAddToPlaylist: MusicPlaylistSnapshotAddTrack;
  previewPlatformFactory: MusicTrackPreviewPlatformFactory | null;
  previewApi?: MusicTrackPreviewApi | null;
  onClone?: MusicPlaylistSnapshotClone | null;
  /** Test ids (Dart `contentKey`, `trackListKey`, `cloneButtonKey`, `doneButtonKey`). */
  contentKey?: string | null;
  trackListKey?: string | null;
  cloneButtonKey?: string | null;
  doneButtonKey?: string | null;
  cloneSuccessMessage?: ((playlist: PersonalMusicPlaylist) => string) | null;
  cloneErrorMessage?: ((error: unknown) => string) | null;
  cloneErrorFallback?: string;
}

interface _MusicPlaylistSnapshotDialogStateProps extends MusicPlaylistSnapshotDialogProps {
  /** Navigator.pop for the dialog route. */
  close: () => void;
  windowHeight: number;
}

/**
 * A reusable, immutable playlist snapshot viewer.
 *
 * Both the active music-box playlist and playlists embedded in chat use this
 * surface so song-card interaction, preview lifecycle and clone limits remain
 * consistent regardless of where the playlist was opened.
 *
 * Shown with `showDialog(() => <MusicPlaylistSnapshotDialog … />)`.
 */
class _MusicPlaylistSnapshotDialogState extends ViewState<_MusicPlaylistSnapshotDialogStateProps> {
  _previewController: MusicTrackPreviewController | null = null;
  _personalPlaylists: readonly PersonalMusicPlaylist[] = [];
  _maxPlaylists = 50;
  _loadingPlaylists = true;
  _cloning = false;
  _clonedPlaylist: PersonalMusicPlaylist | null = null;

  get _playlistLimitReached(): boolean {
    return !this._loadingPlaylists && this._personalPlaylists.length >= this._maxPlaylists;
  }

  override initState(): void {
    this._createPreviewController();
    // The controller exists from the first frame in Flutter; re-render so
    // the track tiles pick it up.
    this.setState();
    unawaited(this._loadPlaylists());
  }

  override didUpdateProps(oldWidget: _MusicPlaylistSnapshotDialogStateProps): void {
    if (
      (oldWidget.previewApi ?? null) !== (this.widget.previewApi ?? null) ||
      oldWidget.previewPlatformFactory !== this.widget.previewPlatformFactory
    ) {
      const previous = this._previewController;
      this._previewController = null;
      if (previous != null) unawaited(previous.dispose());
      this.setState(() => this._createPreviewController());
    }
  }

  _createPreviewController(): void {
    const api = this.widget.previewApi ?? null;
    const factory = this.widget.previewPlatformFactory;
    if (api != null && factory != null) {
      this._previewController = new MusicTrackPreviewController({ api, platform: factory.create() });
    }
  }

  override dispose(): void {
    const preview = this._previewController;
    if (preview != null) unawaited(preview.dispose());
  }

  async _loadPlaylists(): Promise<void> {
    try {
      const page = await this.widget.loadPersonalPlaylists();
      if (!this.mounted) return;
      this.setState(() => {
        this._personalPlaylists = page.playlists;
        this._maxPlaylists = page.maxPlaylists;
        this._loadingPlaylists = false;
      });
    } catch {
      if (this.mounted) {
        this.setState(() => {
          this._loadingPlaylists = false;
        });
      }
    }
  }

  async _clone(): Promise<void> {
    const clone = this.widget.onClone ?? null;
    if (clone == null || this._cloning || this._loadingPlaylists || this._clonedPlaylist != null || this._playlistLimitReached) {
      return;
    }
    this.setState(() => {
      this._cloning = true;
    });
    try {
      const playlist = await clone();
      if (!this.mounted) return;
      this.setState(() => {
        this._clonedPlaylist = playlist;
        this._personalPlaylists = [...this._personalPlaylists, playlist];
      });
      const notice = this.widget.cloneSuccessMessage?.(playlist) ?? `已克隆到我的歌单 - ${playlist.name}`;
      showFloatingSuccessNotice(notice);
    } catch (error) {
      if (this.mounted) {
        showFloatingErrorNotice(
          this.widget.cloneErrorMessage?.(error) ??
            userFacingErrorMessage(error, { fallback: this.widget.cloneErrorFallback ?? '克隆歌单失败，请重试' }),
        );
      }
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._cloning = false;
        });
      }
    }
  }

  build(): ReactNode {
    const widget = this.widget;
    const canClone =
      widget.onClone != null && !this._loadingPlaylists && !this._playlistLimitReached && this._clonedPlaylist == null;
    const cloneLabel =
      this._clonedPlaylist != null
        ? '已克隆到我的歌单'
        : this._loadingPlaylists
          ? '正在检查我的歌单'
          : this._playlistLimitReached
            ? '我的歌单已满'
            : '克隆到我的歌单';
    const height = Math.min(Math.max(widget.windowHeight * 0.68, 260), 620);
    const actions: ResponsiveDialogAction[] = [];
    if (widget.onClone != null) {
      actions.push(
        new ResponsiveDialogAction({
          buttonKey: widget.cloneButtonKey ?? null,
          label: cloneLabel,
          icon: Icons.library_add,
          tone: ButtonTone.primary,
          loading: this._cloning,
          onPressed: canClone ? () => unawaited(this._clone()) : null,
        }),
      );
    }
    actions.push(
      new ResponsiveDialogAction({
        buttonKey: widget.doneButtonKey ?? null,
        label: '完成',
        icon: Icons.check,
        onPressed: () => widget.close(),
      }),
    );
    const previewController = this._previewController;
    const personalPlaylists = this._personalPlaylists;
    return (
      <DialogFrame title={widget.title} icon={Icons.queue_music} maxWidth={760} adaptiveActions={actions}>
        <div
          data-testid={widget.contentKey ?? undefined}
          style={{ height, flexShrink: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}
        >
          {widget.tracks.length === 0 ? (
            <div style={{ flex: '1 1 0', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Text style={{ ...UiTypography.body, color: UiColors.textMuted }}>这个歌单还没有歌曲</Text>
            </div>
          ) : (
            <div
              data-testid={widget.trackListKey ?? undefined}
              style={{ flex: '1 1 0', minHeight: 0, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 10 }}
            >
              {widget.tracks.map((track) => (
                <MusicPlaylistSnapshotTrackTile
                  key={track.id}
                  track={track}
                  previewController={previewController}
                  playlists={personalPlaylists}
                  onAddToPlaylist={(playlist) => widget.onAddToPlaylist(track, playlist)}
                />
              ))}
            </div>
          )}
        </div>
      </DialogFrame>
    );
  }
}

export function MusicPlaylistSnapshotDialog(props: MusicPlaylistSnapshotDialogProps) {
  const route = useRoute<void>();
  const windowHeight = _useWindowHeight();
  const state = useViewState(() => new _MusicPlaylistSnapshotDialogState(), {
    ...props,
    close: () => route.close(),
    windowHeight,
  });
  return <>{state.build()}</>;
}

export interface MusicPlaylistSnapshotTrackTileProps {
  track: MusicTrackCardData;
  previewController: MusicTrackPreviewController | null;
  playlists: readonly PersonalMusicPlaylist[];
  onAddToPlaylist: (playlist: PersonalMusicPlaylist) => Promise<void>;
  /** Test id of the track surface (Dart `surfaceKey`). */
  surfaceKey?: string | null;
}

const _titleStyle: CSSProperties = {
  fontFamily: kClientFontStack,
  color: UiColors.text,
  fontSize: 13,
  fontWeight: 600,
};

const _subtitleStyle: CSSProperties = {
  fontFamily: kClientFontStack,
  color: UiColors.textMuted,
  fontSize: 12,
};

export function MusicPlaylistSnapshotTrackTile({
  track,
  previewController,
  playlists,
  onAddToPlaylist,
  surfaceKey,
}: MusicPlaylistSnapshotTrackTileProps) {
  const ref = useRef<HTMLDivElement | null>(null);
  const constraints = useElementSize(ref);
  const subtitleParts: string[] = [];
  if (track.artists.length > 0) subtitleParts.push(track.artists.join('、'));
  subtitleParts.push(music_box_display.musicBoxSourceLabel(track.source));
  const subtitle = subtitleParts.join(' · ');
  // LayoutBuilder: unmeasured counts as unbounded.
  const maxWidth = constraints?.width ?? Infinity;
  const textWidth = Math.max(maxWidth - 58, 40);
  const surface = (
    <div ref={ref} data-testid={surfaceKey ?? `music-playlist-snapshot-track:${track.id}`} style={{ minWidth: 0 }}>
      <MusicPlaylistTrackSurface>
        <div style={{ paddingTop: 4, paddingBottom: 4 }}>
          <Row>
            <Icon icon={Icons.music_note} size={19} color={UiColors.accent} />
            <SizedBox width={10} />
            <Expanded>
              <Column crossAxisAlignment="start">
                <Text style={_adaptiveListTextStyle({ text: track.title, baseStyle: _titleStyle, width: textWidth })}>
                  {track.title}
                </Text>
                <SizedBox height={3} />
                <Text style={_adaptiveListTextStyle({ text: subtitle, baseStyle: _subtitleStyle, width: textWidth })}>
                  {subtitle}
                </Text>
              </Column>
            </Expanded>
          </Row>
        </div>
      </MusicPlaylistTrackSurface>
    </div>
  );
  const preview = previewController;
  if (preview == null) return surface;
  return (
    <MusicTrackHoverCard data={track} previewController={preview} playlists={playlists} onAddToPlaylist={onAddToPlaylist}>
      {surface}
    </MusicTrackHoverCard>
  );
}

function _adaptiveListTextStyle(opts: { text: string; baseStyle: CSSProperties; width: number }): CSSProperties {
  const { text, baseStyle, width } = opts;
  if (!Number.isFinite(width)) return baseStyle;
  const lineCount = measureLineCount(text.length === 0 ? ' ' : text, baseStyle, width);
  if (lineCount <= 2) return baseStyle;
  const baseSize = typeof baseStyle.fontSize === 'number' ? baseStyle.fontSize : 12;
  const reduction = Math.min(Math.max(lineCount - 2, 1), 2);
  return { ...baseStyle, fontSize: Math.min(Math.max(baseSize - reduction, 10), baseSize) };
}
