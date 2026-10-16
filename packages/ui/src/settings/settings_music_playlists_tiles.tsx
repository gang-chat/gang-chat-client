import { useRef, type CSSProperties, type ReactNode } from 'react';

import type { MusicTrackPreviewController } from '@gang-chat/core/app/music_track_preview';
import { personalPlaylistArtistsLabel, personalPlaylistSourceLabel } from '@gang-chat/core/app/personal_music_playlists';
import type { MusicBoxSearchResult, PersonalMusicPlaylist, PersonalMusicPlaylistItem } from '@gang-chat/core/protocol/models';

import { Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import type { FocusNode, TextEditingController } from '../lib/text_editing';
import { measureTextWidth } from '../lib/text_measure';
import { useElementSize } from '../lib/use_element_size';
import { ButtonIcon, ButtonTone } from '../ui/button';
import { HighlightedText } from '../ui/highlighted_text';
import { Icon, Icons } from '../ui/icon';
import { Input } from '../ui/input';
import { MusicPlaylistTrackSurface } from '../ui/music_playlist_track_surface';
import { UiPointerTapRegion } from '../ui/platform_gestures';
import { UiColors } from '../ui/tokens';
import { MusicTrackCardData, MusicTrackHoverCard, type MusicTrackPlaylistTarget } from '../home/music_track_profile_card';
import { _SettingsSubPanel } from './settings_components';
import { _cyan, _danger, _primaryDark, _textMuted, _textPrimary } from './settings_page';

// Part of the settings page (Dart `part of 'settings_page.dart'`): the list
// rows, tiles and small building blocks of the playlist manager.
//
// PORT: the shared colour aliases (`_cyan`, `_textMuted`, …) come from
// `./settings_page`, which imports this module back (a part-file cycle).
// They are therefore only read inside render functions, never while this
// module is evaluated; module-level constants use the `UiColors` tokens the
// aliases stand for.

// ---------------------------------------------------------------------------
// Playlist summary list

export interface _MusicPlaylistSummaryListProps {
  playlists: readonly PersonalMusicPlaylist[];
  managing: boolean;
  selectionNumbers: ReadonlyMap<string, number>;
  busy: boolean;
  filterActive: boolean;
  onTap: (playlist: PersonalMusicPlaylist) => void;
  onRename: (playlist: PersonalMusicPlaylist) => void;
  onMove: (playlist: PersonalMusicPlaylist, direction: number) => void;
  useCloneAction: boolean;
  cloningPlaylistId: string | null;
  onClone: ((playlist: PersonalMusicPlaylist) => void) | null;
  canShare: boolean;
  sharingPlaylistId: string | null;
  onShare: (playlist: PersonalMusicPlaylist) => void;
}

export function _MusicPlaylistSummaryList({
  playlists,
  managing,
  selectionNumbers,
  busy,
  filterActive,
  onTap,
  onRename,
  onMove,
  useCloneAction,
  cloningPlaylistId,
  onClone,
  canShare,
  sharingPlaylistId,
  onShare,
}: _MusicPlaylistSummaryListProps) {
  // LayoutBuilder: the list's own width. Unmeasured counts as unbounded, like
  // Flutter's non-finite `maxWidth` branch. PORT: `clientWidth` excludes a
  // classic (non-overlay) scrollbar, which is also the width the rows get.
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  const subPanelHorizontalPadding = 28.0;
  const contentWidth = size != null && Number.isFinite(size.width) ? Math.max(0, size.width - subPanelHorizontalPadding) : Infinity;
  const stackControls =
    managing &&
    playlists.some((playlist) =>
      _playlistNameNeedsStackedControls({
        availableWidth: contentWidth,
        name: playlist.name,
        selected: selectionNumbers.has(playlist.id),
      }),
    );
  return (
    <div
      ref={ref}
      data-testid="personal-music-playlists-scroll"
      style={{
        flex: '1 1 0',
        minHeight: 0,
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        boxSizing: 'border-box',
      }}
    >
      {playlists.map((playlist, index) => (
        <div key={playlist.id} style={{ flexShrink: 0, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
          <_MusicPlaylistSummaryTile
            playlist={playlist}
            managing={managing}
            selectionNumber={selectionNumbers.get(playlist.id) ?? null}
            busy={busy}
            canMoveUp={!filterActive && index > 0}
            canMoveDown={!filterActive && index < playlists.length - 1}
            stackControls={stackControls}
            nameMaxLines={
              _playlistNameNeedsTwoLines({
                availableWidth: contentWidth,
                name: playlist.name,
                managing,
                selected: selectionNumbers.has(playlist.id),
                controlsStacked: stackControls,
              })
                ? 2
                : 1
            }
            onTap={() => onTap(playlist)}
            onRename={() => onRename(playlist)}
            onMoveUp={() => onMove(playlist, -1)}
            onMoveDown={() => onMove(playlist, 1)}
            useCloneAction={useCloneAction}
            cloning={cloningPlaylistId === playlist.id}
            onClone={onClone == null ? null : () => onClone(playlist)}
            canShare={canShare}
            sharing={sharingPlaylistId === playlist.id}
            onShare={() => onShare(playlist)}
          />
        </div>
      ))}
    </div>
  );
}

export interface _MusicPlaylistSummaryTileProps {
  playlist: PersonalMusicPlaylist;
  managing: boolean;
  selectionNumber: number | null;
  busy: boolean;
  canMoveUp: boolean;
  canMoveDown: boolean;
  stackControls: boolean;
  nameMaxLines: number;
  onTap: () => void;
  onRename: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
  useCloneAction: boolean;
  cloning: boolean;
  onClone: (() => void) | null;
  canShare: boolean;
  sharing: boolean;
  onShare: () => void;
}

export function _MusicPlaylistSummaryTile({
  playlist,
  managing,
  selectionNumber,
  busy,
  canMoveUp,
  canMoveDown,
  stackControls,
  nameMaxLines,
  onTap,
  onRename,
  onMoveUp,
  onMoveDown,
  useCloneAction,
  cloning,
  onClone,
  canShare,
  sharing,
  onShare,
}: _MusicPlaylistSummaryTileProps) {
  const selected = selectionNumber != null;
  const cardMouseCursor = managing ? SystemMouseCursors.basic : SystemMouseCursors.click;
  const information = (
    <div
      data-testid={`personal-music-playlist-information-${playlist.id}`}
      style={{ paddingTop: 5, paddingBottom: 5, minWidth: 0, boxSizing: 'border-box' }}
    >
      <Row>
        {managing ? (
          <>
            <Icon icon={selected ? Icons.check_box : Icons.check_box_outline_blank} color={selected ? _cyan : _textMuted} size={20} />
            <SizedBox width={10} />
          </>
        ) : null}
        <Icon icon={Icons.queue_music} color={_cyan} size={24} />
        <SizedBox width={12} />
        <Expanded>
          <Column crossAxisAlignment="start">
            <Text maxLines={nameMaxLines} overflow="ellipsis" style={{ ..._playlistSummaryNameStyle, maxWidth: '100%' }}>
              {playlist.name}
            </Text>
            <SizedBox height={3} />
            <Text style={{ color: _textMuted, fontSize: 12, maxWidth: '100%' }}>{`${playlist.itemCount} 首歌曲`}</Text>
          </Column>
        </Expanded>
        {managing && selected ? (
          <_MusicPlaylistSelectionNumber number={selectionNumber} />
        ) : !managing ? (
          <Icon icon={Icons.chevron_right} color={_textMuted} size={22} />
        ) : null}
      </Row>
    </div>
  );
  const controls = (
    <_ManagementCardActionArea style={stackControls && managing ? { flexShrink: 1 } : { flexShrink: 0 }}>
      <_Wrap data-testid={`personal-music-playlist-controls-${playlist.id}`}>
        <ButtonIcon tooltip="重命名" onPressed={busy ? null : onRename} icon={Icons.edit_outlined} size={36} />
        <ButtonIcon tooltip="上移" onPressed={!busy && canMoveUp ? onMoveUp : null} icon={Icons.arrow_upward} size={36} />
        <ButtonIcon tooltip="下移" onPressed={!busy && canMoveDown ? onMoveDown : null} icon={Icons.arrow_downward} size={36} />
        {useCloneAction ? (
          <ButtonIcon
            data-testid={`clone-room-music-playlist-${playlist.id}`}
            tooltip="克隆"
            onPressed={busy || cloning ? null : onClone}
            loading={cloning}
            icon={Icons.library_add_outlined}
            size={36}
          />
        ) : (
          <ButtonIcon
            data-testid={`share-personal-music-playlist-${playlist.id}`}
            tooltip="分享"
            onPressed={busy || !canShare || sharing ? null : onShare}
            loading={sharing}
            icon={Icons.share_outlined}
            size={36}
          />
        )}
      </_Wrap>
    </_ManagementCardActionArea>
  );
  const panel = (
    <_SettingsSubPanel
      testId={`personal-music-playlist-card-${playlist.id}`}
      hoverable
      highlighted={selected}
      mouseCursor={cardMouseCursor}
    >
      {stackControls && managing ? (
        <Column crossAxisAlignment="stretch">
          {information}
          <SizedBox height={10} />
          <Row mainAxisAlignment="end">{controls}</Row>
        </Column>
      ) : (
        <Row>
          <Expanded>{information}</Expanded>
          {managing ? (
            <>
              <SizedBox width={10} />
              {controls}
            </>
          ) : null}
        </Row>
      )}
    </_SettingsSubPanel>
  );
  if (managing) {
    return (
      <_ManagementCardTapTarget onTap={busy ? null : onTap}>{panel}</_ManagementCardTapTarget>
    );
  }
  return (
    <UiPointerTapRegion onTap={busy ? null : onTap} disableSelection>
      {panel}
    </UiPointerTapRegion>
  );
}

function _MusicPlaylistSelectionNumber({ number, testId }: { number: number | null; testId?: string }) {
  return (
    <div
      data-testid={testId}
      style={{
        width: 28,
        height: 28,
        flexShrink: 0,
        borderRadius: '50%',
        backgroundColor: _cyan,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Text softWrap={false} style={{ color: _primaryDark, fontSize: 12, fontWeight: 700 }}>{`${number}`}</Text>
    </div>
  );
}

/** Flutter `Wrap(spacing: 8, runSpacing: 8, alignment: WrapAlignment.end)`. */
function _Wrap({ children, 'data-testid': testId }: { children?: ReactNode; 'data-testid'?: string }) {
  return (
    <div
      data-testid={testId}
      style={{ display: 'flex', flexDirection: 'row', flexWrap: 'wrap', gap: 8, justifyContent: 'flex-end', alignItems: 'flex-start' }}
    >
      {children}
    </div>
  );
}

// _textPrimary
export const _playlistSummaryNameStyle: CSSProperties = {
  color: UiColors.text,
  fontSize: 14,
  fontWeight: 600,
};
export const _playlistSummaryControlsWidth = 4 * 36.0 + 3 * 8.0;
export const _playlistSummaryControlsGap = 10.0;

export function _playlistNameNeedsStackedControls(opts: { availableWidth: number; name: string; selected: boolean }): boolean {
  const { availableWidth, name, selected } = opts;
  const requiredWidth =
    _playlistSummaryInformationFixedWidth({ managing: true, selected }) +
    _playlistSummaryTextWidth(name) +
    _playlistSummaryTruncationGuardWidth() +
    _playlistSummaryControlsGap +
    _playlistSummaryControlsWidth;
  return requiredWidth > availableWidth;
}

export function _playlistNameNeedsTwoLines(opts: {
  availableWidth: number;
  name: string;
  managing: boolean;
  selected: boolean;
  controlsStacked: boolean;
}): boolean {
  const { availableWidth, name, managing, selected, controlsStacked } = opts;
  if (managing && !controlsStacked) return false;
  const requiredWidth =
    _playlistSummaryInformationFixedWidth({ managing, selected }) +
    _playlistSummaryTextWidth(name) +
    _playlistSummaryTruncationGuardWidth();
  return requiredWidth > availableWidth;
}

export function _playlistSummaryInformationFixedWidth(opts: { managing: boolean; selected: boolean }): number {
  const { managing, selected } = opts;
  return (managing ? 20 + 10 : 0) + 24 + 12 + (managing ? (selected ? 28 : 0) : 22);
}

/**
 * PORT: Dart measured with `TextPainter` (context's text direction and text
 * scaler); the DOM port measures the same style on a canvas.
 */
export function _playlistSummaryTextWidth(text: string): number {
  return measureTextWidth(text, _playlistSummaryNameStyle);
}

export function _playlistSummaryTruncationGuardWidth(): number {
  return _playlistSummaryTextWidth('…');
}

// ---------------------------------------------------------------------------
// Track search

export interface _MusicPlaylistSearchRowProps {
  controller: TextEditingController;
  focusNode?: FocusNode | null;
  hintText: string;
  onSubmitted: (value: string) => void;
}

export function _MusicPlaylistSearchRow({ controller, focusNode, hintText, onSubmitted }: _MusicPlaylistSearchRowProps) {
  return (
    <Input
      controller={controller}
      focusNode={focusNode}
      hintText={hintText}
      prefixIcon={Icons.search}
      showClearButton
      onSubmitted={onSubmitted}
    />
  );
}

export interface _MusicPlaylistSearchResultTileProps {
  result: MusicBoxSearchResult;
  query: string;
  loading: boolean;
  onAdd: () => void;
  previewController: MusicTrackPreviewController | null;
  loadPlaylistTargets: () => Promise<MusicTrackPlaylistTarget[]>;
  onAddToPlaylistTarget: (track: MusicBoxSearchResult, target: MusicTrackPlaylistTarget) => Promise<void>;
}

export function _MusicPlaylistSearchResultTile({
  result,
  query,
  loading,
  onAdd,
  previewController,
  loadPlaylistTargets,
  onAddToPlaylistTarget,
}: _MusicPlaylistSearchResultTileProps) {
  const panel = (
    <_SettingsSubPanel testId={`music-playlist-search-result:${result.source}:${result.trackId}`}>
        <Row>
          <Icon icon={Icons.music_note} color={_cyan} size={20} />
          <SizedBox width={10} />
          <Expanded>
            <Column crossAxisAlignment="start">
              <HighlightedText
                text={result.name}
                query={query}
                maxLines={2}
                overflow="ellipsis"
                style={{ color: _textPrimary, fontSize: 13, fontWeight: 600, maxWidth: '100%' }}
              />
              <SizedBox height={3} />
              <HighlightedText
                text={personalPlaylistArtistsLabel(result.artists)}
                query={query}
                maxLines={2}
                overflow="ellipsis"
                style={{ color: _textMuted, fontSize: 12, maxWidth: '100%' }}
              />
            </Column>
          </Expanded>
          <SizedBox width={10} />
          <_ManagementCardActionArea style={{ flexShrink: 0 }}>
            <ButtonIcon
              data-testid={`music-playlist-search-result-add:${result.source}:${result.trackId}`}
              tooltip="添加到当前歌单"
              onPressed={loading ? null : onAdd}
              loading={loading}
              tone={ButtonTone.primary}
              icon={Icons.add}
              size={40}
            />
          </_ManagementCardActionArea>
        </Row>
    </_SettingsSubPanel>
  );
  const preview = previewController;
  if (preview == null) return panel;
  return (
    <MusicTrackHoverCard
      data={
        new MusicTrackCardData({
          id: `${result.source}:${result.trackId}`,
          source: result.source,
          trackId: result.trackId,
          title: result.name,
          artists: result.artists,
          durationMs: 0,
        })
      }
      previewController={preview}
      loadPlaylistTargets={loadPlaylistTargets}
      onAddToPlaylistTarget={(target: MusicTrackPlaylistTarget) => onAddToPlaylistTarget(result, target)}
    >
      {panel}
    </MusicTrackHoverCard>
  );
}

// ---------------------------------------------------------------------------
// Playlist items

export interface _MusicPlaylistItemTileProps {
  item: PersonalMusicPlaylistItem;
  managing: boolean;
  selectionNumber: number | null;
  busy: boolean;
  canMoveUp: boolean;
  canMoveDown: boolean;
  onTap: () => void;
  onShare: (() => void) | null;
  onMoveUp: () => void;
  onMoveDown: () => void;
  previewController: MusicTrackPreviewController | null;
  loadPlaylistTargets: () => Promise<MusicTrackPlaylistTarget[]>;
  onAddToPlaylistTarget: ((target: MusicTrackPlaylistTarget) => Promise<void>) | null;
}

export function _MusicPlaylistItemTile({
  item,
  managing,
  selectionNumber,
  busy,
  canMoveUp,
  canMoveDown,
  onTap,
  onShare,
  onMoveUp,
  onMoveDown,
  previewController,
  loadPlaylistTargets,
  onAddToPlaylistTarget,
}: _MusicPlaylistItemTileProps) {
  const selected = selectionNumber != null;
  // LayoutBuilder inside the track surface: the surface's content width.
  const layoutRef = useRef<HTMLDivElement | null>(null);
  const layoutSize = useElementSize(layoutRef);
  const information = (
    <div style={{ paddingTop: 4, paddingBottom: 4, minWidth: 0, boxSizing: 'border-box' }}>
      <Row>
        {managing ? (
          <>
            <Icon icon={selected ? Icons.check_box : Icons.check_box_outline_blank} size={20} color={selected ? _cyan : _textMuted} />
            <SizedBox width={10} />
          </>
        ) : null}
        <Icon icon={Icons.music_note} size={19} color={_cyan} />
        <SizedBox width={10} />
        <Expanded>
          <Column crossAxisAlignment="start">
            <Text maxLines={2} overflow="ellipsis" style={{ color: _textPrimary, fontSize: 13, fontWeight: 600, maxWidth: '100%' }}>
              {item.title}
            </Text>
            <SizedBox height={3} />
            <Text maxLines={2} overflow="ellipsis" style={{ color: _textMuted, fontSize: 12, maxWidth: '100%' }}>
              {`${personalPlaylistArtistsLabel(item.artists)} · ${personalPlaylistSourceLabel(item.source)}`}
            </Text>
          </Column>
        </Expanded>
        {managing && selected ? (
          <>
            <SizedBox width={10} />
            <_MusicPlaylistSelectionNumber number={selectionNumber} testId={`personal-music-playlist-item-selection-number-${item.id}`} />
          </>
        ) : null}
      </Row>
    </div>
  );
  const narrow = layoutSize != null && layoutSize.width < 420;
  const controls = (
    <_ManagementCardActionArea style={narrow ? { flexShrink: 1 } : { flexShrink: 0 }}>
      <_Wrap>
        <ButtonIcon tooltip="上移" onPressed={!busy && canMoveUp ? onMoveUp : null} icon={Icons.arrow_upward} size={36} />
        <ButtonIcon tooltip="下移" onPressed={!busy && canMoveDown ? onMoveDown : null} icon={Icons.arrow_downward} size={36} />
        {onShare != null ? (
          <ButtonIcon
            data-testid={`share-music-playlist-item-${item.id}`}
            tooltip="分享歌曲"
            onPressed={busy ? null : onShare}
            icon={Icons.share_outlined}
            size={36}
            loading={busy}
          />
        ) : null}
      </_Wrap>
    </_ManagementCardActionArea>
  );
  const panel = (
    <MusicPlaylistTrackSurface highlighted={selected}>
      <div ref={layoutRef} style={{ width: '100%', minWidth: 0 }}>
        {narrow ? (
          <Column crossAxisAlignment="stretch">
            {information}
            {managing ? (
              <>
                <SizedBox height={10} />
                <Row mainAxisAlignment="end">{controls}</Row>
              </>
            ) : null}
          </Column>
        ) : (
          <Row>
            <Expanded>{information}</Expanded>
            {managing ? (
              <>
                <SizedBox width={10} />
                {controls}
              </>
            ) : null}
          </Row>
        )}
      </div>
    </MusicPlaylistTrackSurface>
  );
  if (!managing) {
    const preview = previewController;
    if (preview == null) return panel;
    return (
      <MusicTrackHoverCard
        data={
          new MusicTrackCardData({
            id: item.id,
            source: item.source,
            trackId: item.trackId,
            title: item.title,
            artists: item.artists,
            durationMs: item.durationMs,
          })
        }
        previewController={preview}
        loadPlaylistTargets={loadPlaylistTargets}
        onAddToPlaylistTarget={onAddToPlaylistTarget ?? undefined}
      >
        {panel}
      </MusicTrackHoverCard>
    );
  }
  return <_ManagementCardTapTarget onTap={busy ? null : onTap}>{panel}</_ManagementCardTapTarget>;
}

// ---------------------------------------------------------------------------
// Management-card gesture helpers

export interface _ManagementCardTapTargetProps {
  children?: ReactNode;
  onTap: (() => void) | null;
  /** Dart `key`, exposed as a test id. */
  'data-testid'?: string;
}

/**
 * Makes every non-action part of a management card select the card.
 *
 * Action clusters use [_ManagementCardActionArea], which stops the click from
 * reaching this target (Flutter: its nested recognizer won the gesture
 * arena). This keeps active and disabled action buttons from also toggling
 * the card selection while preserving the full card's padding and layout gaps
 * as useful selection targets.
 */
export function _ManagementCardTapTarget({ children, onTap, 'data-testid': testId }: _ManagementCardTapTargetProps) {
  return (
    <div
      role="button"
      aria-disabled={onTap == null}
      data-testid={testId}
      onClick={(event) => {
        if (event.button !== 0) return;
        onTap?.();
      }}
      style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}
    >
      {children}
    </div>
  );
}

/**
 * PORT: Dart's `GestureDetector(behavior: opaque, onTap: () {})` claimed taps
 * on the whole action cluster (gaps included). The DOM equivalent stops the
 * click from bubbling to card-level tap handlers; raw pointer listeners
 * (like Flutter `Listener`s) still see the events. [style] carries the flex
 * sizing Flutter derived from the parent's constraints.
 */
export function _ManagementCardActionArea({ children, style }: { children?: ReactNode; style?: CSSProperties }) {
  return (
    <div
      onClick={(event) => event.stopPropagation()}
      style={{ display: 'flex', flexDirection: 'column', minWidth: 0, ...style }}
    >
      {children}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Text helpers

export function _MusicPlaylistErrorText({ message }: { message: string }) {
  return <Text style={{ color: _danger, fontSize: 12, lineHeight: 1.4 }}>{message}</Text>;
}

export function _MusicPlaylistFieldLabel({ text }: { text: string }) {
  return (
    <Text maxLines={1} overflow="ellipsis" style={{ color: _textMuted, fontSize: 12, fontWeight: 500 }}>
      {text}
    </Text>
  );
}
