import { useLayoutEffect, useRef, type CSSProperties, type ReactNode } from 'react';

import * as music_box_display from '@gang-chat/core/app/music_box_display';
import { audioVolumePercentText } from '@gang-chat/core/app/audio_levels';
import type { MusicBoxController } from '@gang-chat/core/app/music_box_controller';
import {
  MusicBoxActiveSourceType,
  MusicBoxPlaybackMode,
  MusicBoxPlaybackState,
  type CurrentUser,
  type MusicBoxPlayback,
  type MusicBoxQueueItem,
  type MusicBoxRequester,
  type MusicBoxSearchResult,
  type MusicBoxState,
  type PublicRoom,
} from '@gang-chat/core/protocol/models';

import { EdgeInsets } from '../lib/edge_insets';
import { Column, Expanded, Flexible, Positioned, Row, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import type { TextEditingController } from '../lib/text_editing';
import { useElementSize } from '../lib/use_element_size';
import { ViewState, useViewState } from '../lib/view_state';
import { ButtonIcon, ButtonTone } from '../ui/button';
import { UiContextMenuItem, UiContextMenuSection, showUiContextMenu } from '../ui/context_menu';
import { Icon, Icons, type IconData } from '../ui/icon';
import { rectInWindow } from '../ui/overlay';
import { OverflowMarqueeText } from '../ui/overflow_marquee_text';
import { DotGridBackground, EqualizerBars, SignalTag } from '../ui/signal';
import { UiSlider } from '../ui/slider';
import { PressableSurface } from '../ui/surface';
import { UiColors, UiRadii, UiShadows, UiTypography, argb, boxShadow, withAlpha } from '../ui/tokens';
import { _MusicBoxBody } from './live_channel_music_box_body';
import { _MusicBoxFadeText, _MusicBoxRequesterAvatar, _MusicBoxScaleDown } from './live_channel_music_box_rows';
import { _volumeLevelIcon } from './live_channel_controls';
import type { MusicPlaylistEditCallback } from './music_playlist_profile_card';
import type { RoomProfileResolver, UserProfileActionBuilder, UserProfileResolver } from './room_profile_card';

/**
 * Minimum height retained by the docked console. When the live controls need
 * an extra row, the panel extends upward instead of making the whole console
 * scroll.
 */
export const _musicBoxMinComfortableHeight = 400;

/**
 * Height of the dense controls that sit above the lists: the search field,
 * the source picker beside it, and the tab bar.
 */
export const _musicBoxControlHeight = 30;

export interface LiveMusicBoxPanelProps {
  state: MusicBoxState;
  searchController: TextEditingController;
  searchResults: readonly MusicBoxSearchResult[];
  searching: boolean;
  searchError: string | null;
  source: string;
  onTogglePlayback: () => void;
  onSkip: () => void;
  onPrevious?: (() => void) | null;
  onModeChanged?: ((mode: MusicBoxPlaybackMode) => void) | null;
  controller?: MusicBoxController | null;
  roomId?: string | null;
  playlistsRevision?: number;
  room?: PublicRoom | null;
  onStateChanged?: ((state: MusicBoxState) => void) | null;
  currentUser?: CurrentUser | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder?: UserProfileActionBuilder | null;
  onCreateFirstRoomPlaylist?: (() => void) | null;
  onCreateFirstPersonalPlaylist?: (() => void) | null;
  onEditRoomPlaylist?: MusicPlaylistEditCallback | null;
  onEditPersonalPlaylist?: MusicPlaylistEditCallback | null;
  onQueueResult: (result: MusicBoxSearchResult) => void;
  onRemoveItem: (item: MusicBoxQueueItem) => void;
  onSourceChanged: (source: string) => void;
  onClose: () => void;

  /** Local listening volume for the music box (0–1), restored from the store. */
  volume: number;
  onVolumeChanged: (volume: number) => void;
}

/**
 * The body is one flat list, so navigation is just what is unfolded: the
 * search panel under the queue header, which playlist groups are open, and
 * which single playlist shows its tracks. Realtime snapshots never touch any
 * of these — the user decides what is open; the server decides what plays.
 */
export class _LiveMusicBoxPanelState extends ViewState<LiveMusicBoxPanelProps> {
  _searchOpen = false;
  readonly _openSections = new Set<music_box_display.MusicBoxSection>();
  _openPlaylistKey: string | null = null;
  // Incremented when the header chip asks the body to scroll to the active
  // source; the body reacts to the change, not the value.
  _revealSerial = 0;
  _revealSection: music_box_display.MusicBoxSection | null = null;

  override didUpdateProps(oldWidget: LiveMusicBoxPanelProps): void {
    if (oldWidget.roomId !== this.widget.roomId || oldWidget.controller !== this.widget.controller) {
      this.setState(() => {
        this._searchOpen = false;
        this._openSections.clear();
        this._openPlaylistKey = null;
      });
    }
  }

  _toggleSearch = (): void => {
    this.setState(() => {
      this._searchOpen = !this._searchOpen;
      if (!this._searchOpen) this.widget.searchController.clear();
    });
  };

  _toggleSection = (section: music_box_display.MusicBoxSection): void => {
    this.setState(() => {
      if (this._openSections.has(section)) {
        this._openSections.delete(section);
      } else {
        this._openSections.add(section);
      }
    });
  };

  _togglePlaylist = (type: MusicBoxActiveSourceType, id: string): void => {
    const key = music_box_display.musicBoxPlaylistKey(type, id);
    this.setState(() => {
      this._openPlaylistKey = this._openPlaylistKey === key ? null : key;
    });
  };

  /** Unfold a specific playlist (from a playlist card's 查看歌单). */
  _openPlaylist = (type: MusicBoxActiveSourceType, id: string): void => {
    this.setState(() => {
      this._openSections.add(music_box_display.musicBoxSectionForSource(type));
      this._openPlaylistKey = music_box_display.musicBoxPlaylistKey(type, id);
    });
  };

  /** Header chip: unfold whatever owns the active source and scroll to it. */
  _showActiveSource = (): void => {
    const active = this.widget.state.activeSource;
    const section = music_box_display.musicBoxSectionForSource(active.type);
    this.setState(() => {
      if (section !== music_box_display.MusicBoxSection.queue) {
        this._openSections.add(section);
        this._openPlaylistKey = music_box_display.musicBoxPlaylistKey(active.type, active.id);
      }
      this._revealSection = section;
      this._revealSerial += 1;
    });
  };
}

/**
 * The in-pane music box console. Three fixed regions, none of which morph:
 * a now-playing strip (what is playing, from which source), a persistent
 * search-to-request bar, and a tab bar for 点歌队列 / 房间歌单 / 我的歌单.
 * Audio is delivered separately via the LiveKit session; this is purely the
 * control surface and status display.
 *
 * The panel fills the box its parent positions it in.
 */
export function LiveMusicBoxPanel(props: LiveMusicBoxPanelProps) {
  const state = useViewState(() => new _LiveMusicBoxPanelState(), props);
  const widget = props;
  const musicBox = widget.state;
  return (
    <div
      data-testid="live-music-box-panel"
      style={{
        width: '100%',
        height: '100%',
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'stretch',
        backgroundColor: UiColors.surfaceLow,
        borderRadius: UiRadii.xl,
        border: `1px solid ${UiColors.borderStrong}`,
        boxShadow: boxShadow(UiShadows.popover),
        // A Flutter DecoratedBox border does not inset its child; the Dart
        // Padding(10, 10, 10, 8) is measured from the outer edge, so the CSS
        // padding drops the 1px border width.
        padding: '9px 9px 7px 9px',
      }}
    >
      <_MusicBoxHeader state={musicBox} onOpenSource={state._showActiveSource} onClose={widget.onClose} />
      <SizedBox height={8} />
      <_MusicBoxNowPlaying
        state={musicBox}
        onTogglePlayback={widget.onTogglePlayback}
        onSkip={widget.onSkip}
        onPrevious={widget.onPrevious ?? null}
        onModeChanged={widget.onModeChanged ?? null}
        currentUser={widget.currentUser}
        onResolveUserProfile={widget.onResolveUserProfile}
        onResolveRoomProfile={widget.onResolveRoomProfile}
        onEnterCommonRoom={widget.onEnterCommonRoom}
        userProfileActionBuilder={widget.userProfileActionBuilder}
      />
      <SizedBox height={8} />
      <_MusicBoxVolume initialVolume={widget.volume} onChanged={widget.onVolumeChanged} />
      <SizedBox height={10} />
      <Expanded>
        {/* DecoratedBox(background, lg, border) > ClipRRect(lg - 1) >
            Padding(6, 6, 6, 0). Laid out like the Flutter tree: the clip and
            the padding are measured from the outer edge, and the border is
            painted behind the clipped list. */}
        <div style={{ position: 'relative', flex: '1 1 0', minHeight: 0, backgroundColor: UiColors.background, borderRadius: UiRadii.lg }}>
          <div
            aria-hidden="true"
            style={{
              position: 'absolute',
              inset: 0,
              boxSizing: 'border-box',
              borderRadius: UiRadii.lg,
              border: `1px solid ${UiColors.border}`,
              pointerEvents: 'none',
            }}
          />
          <div style={{ position: 'absolute', inset: 0, borderRadius: UiRadii.lg - 1, overflow: 'hidden' }}>
            <div style={{ position: 'absolute', top: 6, left: 6, right: 6, bottom: 0, display: 'flex', flexDirection: 'column' }}>
              <_MusicBoxBody
                state={musicBox}
                searchOpen={state._searchOpen}
                openSections={state._openSections}
                openPlaylistKey={state._openPlaylistKey}
                revealSerial={state._revealSerial}
                revealSection={state._revealSection}
                onToggleSearch={state._toggleSearch}
                onToggleSection={state._toggleSection}
                onTogglePlaylist={state._togglePlaylist}
                onOpenPlaylist={state._openPlaylist}
                searchController={widget.searchController}
                searchResults={widget.searchResults}
                searching={widget.searching}
                searchError={widget.searchError}
                source={widget.source}
                onQueueResult={widget.onQueueResult}
                onRemoveItem={widget.onRemoveItem}
                onSourceChanged={widget.onSourceChanged}
                controller={widget.controller ?? null}
                roomId={widget.roomId ?? null}
                playlistsRevision={widget.playlistsRevision ?? 0}
                room={widget.room ?? null}
                onStateChanged={widget.onStateChanged ?? null}
                currentUser={widget.currentUser ?? null}
                onResolveUserProfile={widget.onResolveUserProfile ?? null}
                onResolveRoomProfile={widget.onResolveRoomProfile ?? null}
                onEnterCommonRoom={widget.onEnterCommonRoom ?? null}
                userProfileActionBuilder={widget.userProfileActionBuilder ?? null}
                onCreateFirstRoomPlaylist={widget.onCreateFirstRoomPlaylist ?? null}
                onCreateFirstPersonalPlaylist={widget.onCreateFirstPersonalPlaylist ?? null}
                onEditRoomPlaylist={widget.onEditRoomPlaylist ?? null}
                onEditPersonalPlaylist={widget.onEditPersonalPlaylist ?? null}
              />
            </div>
          </div>
        </div>
      </Expanded>
    </div>
  );
}

/**
 * Narrowest header slot in which the active-source chip still shows its
 * label: the chip's fixed parts (border, padding, source mark, arrow) take
 * 53px, leaving room for at least a couple of characters.
 */
export const _musicBoxSourceChipMinLabelWidth = 80;

/**
 * Title row: the dot-matrix wordmark, the active-source chip (jumps to the
 * list that owns what is playing) and the close key.
 */
export function _MusicBoxHeader({ state, onOpenSource, onClose }: { state: MusicBoxState; onOpenSource: () => void; onClose: () => void }) {
  const active = state.activeSource;
  const playing = music_box_display.musicBoxHasActivePlayback(state);
  // LayoutBuilder over the chip slot.
  const slotRef = useRef<HTMLDivElement | null>(null);
  const slotSize = useElementSize(slotRef);
  // The docked deck can be as narrow as the live room's inner width on a
  // 320px phone; there the chip keeps only its source mark (the tooltip
  // still names it) instead of overflowing.
  const showLabel = slotSize == null || slotSize.width >= _musicBoxSourceChipMinLabelWidth;
  return (
    <Row>
      <SizedBox width={4} />
      <Text
        role="img"
        aria-label="音乐盒"
        softWrap={false}
        style={{ ...UiTypography.dot, fontSize: 15, color: UiColors.text, letterSpacing: 0.5 }}
      >
        MUSIC BOX
      </Text>
      <SizedBox width={10} />
      <Expanded ref={slotRef} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end' }}>
        <PressableSurface
          data-testid="music-box-active-source-chip"
          height={26}
          tooltip={playing ? '正在播放的来源' : '当前来源'}
          onPressed={onOpenSource}
          padding={EdgeInsets.symmetric({ horizontal: 9 })}
          backgroundColor={playing ? UiColors.selected : UiColors.surface}
          selected={false}
          borderColor={playing ? UiColors.accentBorder : UiColors.border}
          borderRadius={UiRadii.pill}
          style={{ flexShrink: 1, minWidth: 0, maxWidth: '100%' }}
        >
          <Row mainAxisSize="min" style={{ maxWidth: '100%' }}>
            {playing ? (
              <EqualizerBars height={10} barWidth={2} gap={1.5} />
            ) : (
              <Icon icon={_musicBoxSourceTypeIcon(active.type)} size={13} color={UiColors.textSecondary} />
            )}
            {showLabel ? (
              <>
                <SizedBox width={6} />
                {/* Flexible so a long source name fades inside the chip
                    instead of overflowing a narrow deck header. */}
                <Flexible style={{ maxWidth: 120 }}>
                  <_MusicBoxFadeText
                    text={music_box_display.musicBoxActiveSourceLabel(active)}
                    style={{
                      ...UiTypography.label,
                      color: playing ? UiColors.accent : UiColors.text,
                      fontSize: 11,
                      fontWeight: 700,
                    }}
                  />
                </Flexible>
                <SizedBox width={2} />
                <Icon icon={Icons.arrow_outward_rounded} size={12} color={playing ? UiColors.accent : UiColors.textMuted} />
              </>
            ) : null}
          </Row>
        </PressableSurface>
      </Expanded>
      <SizedBox width={6} />
      <ButtonIcon
        data-testid="music-box-close"
        icon={Icons.close_rounded}
        tooltip="关闭音乐盒"
        onPressed={onClose}
        ghost
        size={28}
      />
    </Row>
  );
}

export function _musicBoxSourceTypeIcon(type: MusicBoxActiveSourceType): IconData {
  switch (type) {
    case MusicBoxActiveSourceType.temporary:
      return Icons.queue_music;
    case MusicBoxActiveSourceType.roomPlaylist:
      return Icons.meeting_room;
    case MusicBoxActiveSourceType.userPlaylist:
      return Icons.person;
  }
}

export interface _MusicBoxNowPlayingProps {
  state: MusicBoxState;
  onTogglePlayback: () => void;
  onSkip: () => void;
  onPrevious: (() => void) | null;
  onModeChanged: ((mode: MusicBoxPlaybackMode) => void) | null;
  currentUser?: CurrentUser | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder?: UserProfileActionBuilder | null;
}

/** Dart `_MusicBoxNowPlaying._recordSize`. */
const _recordSize = 104;

/**
 * The deck: a black dot-matrix display (status, title, artist and requester,
 * dotted progress) with the record spinning half out of its right edge, then
 * a row of hardware-style transport keys. Members the server does not let
 * control playback see a single locked line instead of disabled keys.
 */
export function _MusicBoxNowPlaying({
  state,
  onTogglePlayback,
  onSkip,
  onPrevious,
  onModeChanged,
  currentUser,
  onResolveUserProfile,
  onResolveRoomProfile,
  onEnterCommonRoom,
  userProfileActionBuilder,
}: _MusicBoxNowPlayingProps) {
  const current = state.currentItem;
  const spinning = music_box_display.musicBoxRecordSpinning(state);
  const canControl = state.playback.capabilities.canControl;
  const requester = state.activeSource.type === MusicBoxActiveSourceType.temporary ? (current?.requestedBy ?? null) : null;
  const artist = current?.artist.trim() ?? '';
  let statusLabel: string;
  if (current == null) {
    statusLabel = 'IDLE';
  } else {
    switch (state.playback.state) {
      case MusicBoxPlaybackState.playing:
        statusLabel = 'NOW PLAYING';
        break;
      case MusicBoxPlaybackState.paused:
        statusLabel = 'PAUSED';
        break;
      case MusicBoxPlaybackState.stopped:
        statusLabel = 'STOPPED';
        break;
    }
  }
  const live = current != null && state.playback.state === MusicBoxPlaybackState.playing;

  // DecoratedBox(surfacePressed, lg, border) > ClipRRect(lg - 1) > Stack.
  // The border is painted behind the clipped stack and the stack children
  // are positioned against the outer edge, as in Flutter.
  const display = (
    <div style={{ position: 'relative', backgroundColor: UiColors.surfacePressed, borderRadius: UiRadii.lg }}>
      <div
        aria-hidden="true"
        style={{
          position: 'absolute',
          inset: 0,
          boxSizing: 'border-box',
          borderRadius: UiRadii.lg,
          border: `1px solid ${UiColors.border}`,
          pointerEvents: 'none',
        }}
      />
      <div style={{ position: 'relative', borderRadius: UiRadii.lg - 1, overflow: 'hidden' }}>
        <Positioned fill>
          <DotGridBackground color={UiColors.transparent} dotColor={argb(0x12ffffff)} spacing={6} dotRadius={0.6} />
        </Positioned>
        {live ? <_MusicBoxDisplayGlow /> : null}
        <Positioned
          right={-_recordSize * 0.42}
          top={0}
          bottom={0}
          style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}
        >
          <_VinylRecord spinning={spinning} label={current?.title ?? null} size={_recordSize} />
        </Positioned>
        <Column
          crossAxisAlignment="stretch"
          mainAxisSize="min"
          style={{ position: 'relative', padding: `10px ${_recordSize * 0.58 + 10}px 10px 12px` }}
        >
          <Row>
            <Flexible>
              <SignalTag label={statusLabel} color={live ? UiColors.accent : UiColors.textMuted} dot />
            </Flexible>
          </Row>
          <SizedBox height={8} />
          <div data-testid="music-box-now-playing:title" style={{ minWidth: 0 }}>
            <OverflowMarqueeText
              trackKey="music-box-now-playing:title-marquee-track"
              text={current?.title ?? '未在播放'}
              style={{
                ...UiTypography.title,
                color: current == null ? UiColors.textMuted : UiColors.text,
                fontSize: 16,
                lineHeight: 1.25,
              }}
            />
          </div>
          <SizedBox height={3} />
          <Row>
            <Flexible>
              <_MusicBoxFadeText
                text={current == null ? '在下方搜索歌曲点歌' : artist.length === 0 ? '未知艺人' : artist}
                style={{ ...UiTypography.mono, color: UiColors.textSecondary, fontSize: 11 }}
              />
            </Flexible>
            {requester != null ? (
              <>
                <SizedBox width={8} />
                <_MusicBoxRequesterChip
                  data-testid="music-box-now-playing:requester"
                  requester={requester}
                  currentUser={currentUser}
                  onResolveUserProfile={onResolveUserProfile}
                  onResolveRoomProfile={onResolveRoomProfile}
                  onEnterCommonRoom={onEnterCommonRoom}
                  userProfileActionBuilder={userProfileActionBuilder}
                />
              </>
            ) : null}
          </Row>
          <SizedBox height={12} />
          <_MusicBoxProgressBar state={state} />
        </Column>
      </div>
    </div>
  );

  return (
    <Column crossAxisAlignment="stretch" mainAxisSize="min">
      {display}
      <SizedBox height={8} />
      {!canControl ? (
        <Row
          mainAxisAlignment="center"
          style={{
            height: 40,
            boxSizing: 'border-box',
            borderRadius: UiRadii.md,
            border: `1px solid ${UiColors.border}`,
          }}
        >
          <Icon icon={Icons.lock_outline_rounded} size={14} color={UiColors.textMuted} />
          <SizedBox width={6} />
          <Text
            data-testid="music-box-transport-locked"
            softWrap={false}
            style={{ ...UiTypography.label, color: UiColors.textMuted, fontWeight: 600 }}
          >
            播放由房间管理员控制
          </Text>
        </Row>
      ) : (
        <_MusicBoxTransportKeys
          state={state}
          onTogglePlayback={onTogglePlayback}
          onSkip={onSkip}
          onPrevious={onPrevious}
          onModeChanged={onModeChanged}
        />
      )}
    </Column>
  );
}

/**
 * The live radial glow behind the display (Dart `RadialGradient(center:
 * Alignment(1, 0), radius: 1.1)`). Flutter's radius is a fraction of the
 * shortest side, so the box is measured.
 */
function _MusicBoxDisplayGlow() {
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  const radius = size == null ? 0 : Math.min(size.width, size.height) * 1.1;
  return (
    <Positioned
      ref={ref}
      fill
      style={{
        backgroundImage:
          radius > 0
            ? `radial-gradient(circle ${radius}px at 100% 50%, ${withAlpha(UiColors.accent, 0.14)} 0%, ${withAlpha(UiColors.accent, 0)} 100%)`
            : undefined,
      }}
    />
  );
}

/**
 * The transport keys (Dart: the `LayoutBuilder` branch of
 * `_MusicBoxNowPlaying`). A unit is a fifth of the row after the gaps; play
 * takes two units.
 */
function _MusicBoxTransportKeys({
  state,
  onTogglePlayback,
  onSkip,
  onPrevious,
  onModeChanged,
}: {
  state: MusicBoxState;
  onTogglePlayback: () => void;
  onSkip: () => void;
  onPrevious: (() => void) | null;
  onModeChanged: ((mode: MusicBoxPlaybackMode) => void) | null;
}) {
  const rowRef = useRef<HTMLDivElement | null>(null);
  const rowSize = useElementSize(rowRef);
  // Dart `Builder`: the mode key's own box anchors the mode menu.
  const modeKeyRef = useRef<HTMLDivElement | null>(null);
  const transport = music_box_display.musicBoxPrimaryTransport(state);
  const hasQueue = state.queue.length > 0;
  const gap = 6;
  const unit = Math.min(Math.max(((rowSize?.width ?? 0) - gap * 3) / 5, 30), 120);
  let primaryTooltip: string;
  switch (transport) {
    case music_box_display.MusicBoxTransportAction.pause:
      primaryTooltip = '暂停';
      break;
    case music_box_display.MusicBoxTransportAction.resume:
      primaryTooltip = '继续播放';
      break;
    case music_box_display.MusicBoxTransportAction.play:
      primaryTooltip = '播放';
      break;
  }
  return (
    <Row ref={rowRef}>
      <div ref={modeKeyRef} style={{ display: 'flex', flexShrink: 0 }}>
        <ButtonIcon
          data-testid="music-box-transport-mode"
          icon={_musicBoxModeIcon(state.playback.mode)}
          tooltip={`播放模式：${_musicBoxModeLabel(state.playback.mode)}`}
          selected={state.playback.mode !== MusicBoxPlaybackMode.sequential}
          onPressed={
            state.playback.capabilities.canChangeMode && onModeChanged != null
              ? () => void _showMusicBoxModeMenu(modeKeyRef.current, state.playback, onModeChanged)
              : null
          }
          size={40}
          width={unit}
          radius={UiRadii.md}
          iconSize={18}
        />
      </div>
      <SizedBox width={gap} />
      <ButtonIcon
        data-testid="music-box-transport-previous"
        icon={Icons.skip_previous_rounded}
        tooltip="上一首"
        onPressed={state.playback.canPrevious ? onPrevious : null}
        size={40}
        width={unit}
        radius={UiRadii.md}
        iconSize={20}
      />
      <SizedBox width={gap} />
      <ButtonIcon
        data-testid="music-box-primary-playback"
        icon={transport === music_box_display.MusicBoxTransportAction.pause ? Icons.pause_rounded : Icons.play_arrow_rounded}
        tooltip={primaryTooltip}
        tone={ButtonTone.primary}
        onPressed={hasQueue ? onTogglePlayback : null}
        size={40}
        width={unit * 2}
        radius={UiRadii.md}
        iconSize={24}
      />
      <SizedBox width={gap} />
      <ButtonIcon
        data-testid="music-box-transport-next"
        icon={Icons.skip_next_rounded}
        tooltip="下一首"
        onPressed={hasQueue && state.playback.canNext ? onSkip : null}
        size={40}
        width={unit}
        radius={UiRadii.md}
        iconSize={20}
      />
    </Row>
  );
}

export interface _MusicBoxRequesterChipProps {
  requester: MusicBoxRequester;
  currentUser?: CurrentUser | null;
  onResolveUserProfile?: UserProfileResolver | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onEnterCommonRoom?: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder?: UserProfileActionBuilder | null;
  /** Dart `key` (test id). */
  'data-testid'?: string;
}

/**
 * Avatar + name for the person who requested the current song. The avatar
 * carries the user card; the name is spelled out, so no tooltip is needed.
 */
export function _MusicBoxRequesterChip({
  requester,
  currentUser,
  onResolveUserProfile,
  onResolveRoomProfile,
  onEnterCommonRoom,
  userProfileActionBuilder,
  'data-testid': testId,
}: _MusicBoxRequesterChipProps) {
  return (
    <Row data-testid={testId} mainAxisSize="min" style={{ flexShrink: 0 }}>
      <_MusicBoxRequesterAvatar
        requester={requester}
        currentUser={currentUser}
        onResolveUserProfile={onResolveUserProfile}
        onResolveRoomProfile={onResolveRoomProfile}
        onEnterCommonRoom={onEnterCommonRoom}
        userProfileActionBuilder={userProfileActionBuilder}
        size={16}
      />
      <SizedBox width={5} />
      <div style={{ maxWidth: 72, minWidth: 0 }}>
        <_MusicBoxFadeText
          text={requester.displayName}
          style={{ ...UiTypography.label, color: UiColors.textMuted, fontSize: 10.5, fontWeight: 600 }}
        />
      </div>
    </Row>
  );
}

export function _musicBoxModeIcon(mode: MusicBoxPlaybackMode): IconData {
  switch (mode) {
    case MusicBoxPlaybackMode.sequential:
      return Icons.format_list_numbered;
    case MusicBoxPlaybackMode.repeatOne:
      return Icons.repeat_one;
    case MusicBoxPlaybackMode.repeatAll:
      return Icons.repeat;
    case MusicBoxPlaybackMode.shuffle:
      return Icons.shuffle;
  }
}

export function _musicBoxModeLabel(mode: MusicBoxPlaybackMode): string {
  switch (mode) {
    case MusicBoxPlaybackMode.sequential:
      return '顺序播放';
    case MusicBoxPlaybackMode.repeatOne:
      return '单曲循环';
    case MusicBoxPlaybackMode.repeatAll:
      return '列表循环';
    case MusicBoxPlaybackMode.shuffle:
      return '随机播放';
  }
}

/**
 * Opens the playback-mode menu at the bottom-right corner of [anchor] (the
 * mode key). PORT: the Dart `BuildContext` is replaced by the key's element.
 */
export function _showMusicBoxModeMenu(
  anchor: HTMLElement | null,
  playback: MusicBoxPlayback,
  onChanged: (mode: MusicBoxPlaybackMode) => void,
): Promise<void> {
  const box = rectInWindow(anchor);
  const position = box == null ? { x: 0, y: 0 } : { x: box.x + box.width, y: box.y + box.height };
  return showUiContextMenu({
    position,
    sections: [
      new UiContextMenuSection(
        playback.capabilities.allowedModes.map(
          (mode) =>
            new UiContextMenuItem({
              label: _musicBoxModeLabel(mode),
              icon: _musicBoxModeIcon(mode),
              selected: mode === playback.mode,
              onPressed: () => onChanged(mode),
            }),
        ),
      ),
    ],
  });
}

// ---------------------------------------------------------------------------
// Volume fader

export interface _MusicBoxVolumeProps {
  initialVolume: number;
  onChanged: (volume: number) => void;
}

/**
 * A flat volume control shaped like a single icon button at rest that
 * elongates rightward on hover, the extra width revealing an inline
 * [UiSlider] within the same pill. Drives the local listening volume of the
 * music box bot's audio track via [onChanged] — purely a per-listener
 * preference, independent of the room's output volume. [initialVolume] seeds
 * it from the restored store; the widget then owns the value while mounted.
 */
export class _MusicBoxVolumeState extends ViewState<_MusicBoxVolumeProps> {
  // The mute key size; the strip is a little taller.
  static readonly _size = 24;
  static readonly _pad = 12;

  _volume: number;
  // The level to restore to when unmuting; captured at the moment of muting.
  _premute: number;
  _hovered = false;
  // True while a slider drag is in flight. The pointer gets captured by the
  // slider for the whole drag, so the cursor can wander off the pill (firing
  // mouseleave) without meaning to collapse it — keep it open until the drag
  // ends, then fall back to the hover state.
  _dragging = false;

  // The fader strip is always fully shown; hover/drag only light its frame.
  static readonly _expandedByDefault = true;

  constructor(props: _MusicBoxVolumeProps) {
    super();
    this._volume = props.initialVolume;
    this._premute = props.initialVolume > 0 ? props.initialVolume : 0.7;
  }

  get _expanded(): boolean {
    return _MusicBoxVolumeState._expandedByDefault || this._hovered || this._dragging;
  }

  get _muted(): boolean {
    return this._volume <= 0;
  }

  override didUpdateProps(oldWidget: _MusicBoxVolumeProps): void {
    if (oldWidget.initialVolume === this.widget.initialVolume) return;
    const nextVolume = Math.min(Math.max(this.widget.initialVolume, 0), 1);
    this.setState(() => {
      this._volume = nextVolume;
      if (nextVolume > 0) this._premute = nextVolume;
    });
  }

  _setVolume = (value: number): void => {
    this.setState(() => {
      this._volume = value;
    });
    this.widget.onChanged(value);
  };

  _toggleMute = (): void => {
    if (this._muted) {
      this._setVolume(this._premute > 0 ? this._premute : 0.7);
    } else {
      this._premute = this._volume;
      this._setVolume(0);
    }
  };

  get _icon(): IconData {
    return _volumeLevelIcon(this._volume);
  }

  get _palette(): { background: string; border: string; foreground: string } {
    const active = !this._muted;
    return {
      background: active ? UiColors.selected : UiColors.surface,
      border: active ? UiColors.selectedBorder : UiColors.border,
      foreground: active ? UiColors.accent : UiColors.text,
    };
  }

  _iconButton(size: number): ReactNode {
    return <_VolumeIconButton icon={this._icon} color={this._palette.foreground} size={size} onTap={this._toggleMute} />;
  }

  _slider(): ReactNode {
    return (
      <UiSlider
        value={this._volume}
        hoverLabel={audioVolumePercentText(this._volume)}
        onChangeStart={() =>
          this.setState(() => {
            this._dragging = true;
          })
        }
        onChangeEnd={() =>
          this.setState(() => {
            this._dragging = false;
          })
        }
        onChanged={this._setVolume}
      />
    );
  }
}

// A flat fader strip: mute key, the slider, and the level in dot numerals.
export function _MusicBoxVolume(props: _MusicBoxVolumeProps) {
  const state = useViewState(() => new _MusicBoxVolumeState(props), props);
  const p = state._palette;
  const size = _MusicBoxVolumeState._size;
  const pad = _MusicBoxVolumeState._pad;
  return (
    <div
      data-testid="music-box-volume-control"
      onMouseEnter={() =>
        state.setState(() => {
          state._hovered = true;
        })
      }
      onMouseLeave={() =>
        state.setState(() => {
          state._hovered = false;
        })
      }
      style={{
        height: size + 8,
        flexShrink: 0,
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: UiColors.surface,
        borderRadius: UiRadii.md,
        border: `1px solid ${state._expanded && state._hovered ? UiColors.borderStrong : UiColors.border}`,
      }}
    >
      {/* The Dart DecoratedBox border does not inset the row: the outer
          spacers drop the 1px border width so every child keeps its x. */}
      <SizedBox width={4 - 1} />
      {state._iconButton(size + 4)}
      <Text
        softWrap={false}
        style={{
          ...UiTypography.overline,
          color: p.foreground === UiColors.accent ? UiColors.textSecondary : UiColors.textMuted,
          fontSize: 9.5,
        }}
      >
        VOL
      </Text>
      <SizedBox width={10} />
      <Expanded>{state._slider()}</Expanded>
      <SizedBox width={10} />
      <SizedBox width={30}>
        <Text
          softWrap={false}
          textAlign="right"
          style={{ ...UiTypography.dot, fontSize: 14, color: state._muted ? UiColors.textMuted : UiColors.accent }}
        >
          {String(Math.round(state._volume * 100)).padStart(2, '0')}
        </Text>
      </SizedBox>
      <SizedBox width={pad - 2 - 1} />
    </div>
  );
}

/** A bare, borderless tap target for the mute icon inside the volume pill. */
export function _VolumeIconButton({ icon, color, size, onTap }: { icon: IconData; color: string; size: number; onTap: () => void }) {
  return (
    <div
      role="button"
      onClick={(event) => {
        if (event.button !== 0) return;
        onTap();
      }}
      style={{
        width: size,
        height: size,
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: SystemMouseCursors.click,
      }}
    >
      <Icon icon={icon} color={color} size={size * 0.46} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Vinyl record

export interface _VinylRecordProps {
  spinning: boolean;
  label?: string | null;
  size?: number;
}

/** One revolution (Dart `AnimationController(duration: 6s)..repeat()`). */
const _vinylRevolution = 6000;

/**
 * A spinning black vinyl record. Rotates continuously while [spinning], and
 * freezes its current angle when paused/stopped — an at-a-glance read of
 * whether the music box is playing.
 *
 * The disc turns with the Web Animations API (pausing an animation keeps its
 * current angle, like `AnimationController.stop()`); the sheen is a separate
 * layer that does not rotate.
 */
export function _VinylRecord({ spinning, size = 40 }: _VinylRecordProps) {
  const discRef = useRef<HTMLDivElement | null>(null);
  const animationRef = useRef<Animation | null>(null);

  // initState / dispose: one endless rotation, created paused.
  useLayoutEffect(() => {
    const node = discRef.current;
    if (node == null || typeof node.animate !== 'function') return;
    const animation = node.animate([{ transform: 'rotate(0turn)' }, { transform: 'rotate(1turn)' }], {
      duration: _vinylRevolution,
      iterations: Number.POSITIVE_INFINITY,
      easing: 'linear',
    });
    animation.pause();
    animationRef.current = animation;
    return () => {
      animation.cancel();
      animationRef.current = null;
    };
  }, []);

  // didUpdateWidget: start, or freeze at the current angle rather than
  // snapping back to zero.
  useLayoutEffect(() => {
    const animation = animationRef.current;
    if (animation == null) return;
    if (spinning && animation.playState !== 'running') {
      animation.play();
    } else if (!spinning && animation.playState === 'running') {
      animation.pause();
    }
  }, [spinning]);

  return (
    <div style={{ position: 'relative', width: size, height: size, flexShrink: 0 }}>
      <div ref={discRef} style={{ position: 'absolute', inset: 0 }}>
        <_VinylPainter size={size} />
      </div>
      {/* The light catching the grooves stays put while the disc turns. */}
      <_VinylSheenPainter size={size} />
    </div>
  );
}

/** Port of the Dart `_VinylPainter` as SVG. */
function _VinylPainter({ size }: { size: number }) {
  const center = size / 2;
  const radius = size / 2;
  const grooves: ReactNode[] = [];
  let index = 0;
  for (let r = radius - 4; r > radius * 0.4; r -= 2.6) {
    grooves.push(
      <circle
        key={index}
        cx={center}
        cy={center}
        r={r}
        fill="none"
        strokeWidth={0.8}
        stroke={index % 2 === 0 ? argb(0xff1a1a1e) : argb(0xff131316)}
      />,
    );
    index += 1;
  }

  // Lime label with a printed stripe so the rotation reads at a glance. The
  // stripe is the Dart rect clipped to the label circle, drawn as the
  // equivalent chord-and-arc path.
  const labelRadius = radius * 0.36;
  const stripeTop = -labelRadius * 0.62;
  const stripeBottom = stripeTop + labelRadius * 0.28;
  const halfChord = (dy: number) => Math.sqrt(Math.max(labelRadius * labelRadius - dy * dy, 0));
  const topHalf = halfChord(stripeTop);
  const bottomHalf = halfChord(stripeBottom);
  const stripe =
    `M ${center - topHalf} ${center + stripeTop} L ${center + topHalf} ${center + stripeTop} ` +
    `A ${labelRadius} ${labelRadius} 0 0 1 ${center + bottomHalf} ${center + stripeBottom} ` +
    `L ${center - bottomHalf} ${center + stripeBottom} ` +
    `A ${labelRadius} ${labelRadius} 0 0 1 ${center - topHalf} ${center + stripeTop} Z`;

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true" style={{ display: 'block' }}>
      <circle cx={center} cy={center} r={radius} fill={argb(0xff0b0b0d)} />
      <circle cx={center} cy={center} r={radius - 0.5} fill="none" stroke={UiColors.borderStrong} strokeWidth={1} />
      {grooves}
      <circle cx={center} cy={center} r={labelRadius} fill={UiColors.accent} />
      <path d={stripe} fill={withAlpha(UiColors.onAccent, 0.85)} />
      <circle cx={center} cy={center} r={labelRadius * 0.16} fill={UiColors.surfacePressed} />
    </svg>
  );
}

/**
 * Port of the Dart `_VinylSheenPainter`: an even-odd ring (radius - 2 down to
 * radius * 0.38) filled with a sweep gradient. Flutter's sweep starts at
 * 3 o'clock; CSS conic gradients start at 12, hence `from 90deg`.
 */
function _VinylSheenPainter({ size }: { size: number }) {
  const radius = size / 2;
  const outer = radius - 2;
  const inner = radius * 0.38;
  const white = (alpha: number) => withAlpha(UiColors.white, alpha);
  const sheen =
    `conic-gradient(from 90deg at 50% 50%, ${white(0)} 0%, ${white(0.07)} 10%, ${white(0)} 22%, ` +
    `${white(0)} 50%, ${white(0.05)} 60%, ${white(0)} 72%)`;
  const ring = `radial-gradient(circle at 50% 50%, transparent ${inner}px, #000 ${inner}px, #000 ${outer}px, transparent ${outer}px)`;
  const style: CSSProperties = {
    position: 'absolute',
    inset: 0,
    pointerEvents: 'none',
    backgroundImage: sheen,
    maskImage: ring,
    WebkitMaskImage: ring,
  };
  return <div aria-hidden="true" style={style} />;
}

// ---------------------------------------------------------------------------
// Progress

/**
 * Server-authoritative progress, drawn as a row of dots that light up as
 * the song plays. The server pushes a fresh snapshot every second, so the
 * bar renders the snapshot's reported position — no local stepping.
 */
export function _MusicBoxProgressBar({ state }: { state: MusicBoxState }) {
  const progress = music_box_display.musicBoxProgress(state);
  const hasTrack = state.currentItem != null;
  const mutedSmall: CSSProperties = { ...UiTypography.mono, fontSize: 10, color: UiColors.textMuted, lineHeight: 1 };
  return (
    <Column crossAxisAlignment="stretch" mainAxisSize="min">
      <div
        role="progressbar"
        aria-valuetext={`${Math.round(progress.fraction * 100)}%`}
        style={{ height: 6, position: 'relative' }}
      >
        <_DotProgressPainter fraction={progress.fraction} active={hasTrack} />
      </div>
      <SizedBox height={7} />
      {/* Scales the readout down only when the display is narrower than it
          (the docked deck on a 320px phone); otherwise it draws at full
          size. */}
      <_MusicBoxScaleDown alignment="bottomLeft" boundedHeight={false}>
        <Row mainAxisSize="min" crossAxisAlignment="end">
          <Text
            softWrap={false}
            style={{ ...UiTypography.dot, fontSize: 17, lineHeight: 1, color: hasTrack ? UiColors.text : UiColors.textMuted }}
          >
            {music_box_display.musicBoxFormatDuration(progress.positionMs)}
          </Text>
          <SizedBox width={6} />
          <Text softWrap={false} style={mutedSmall}>
            /
          </Text>
          <SizedBox width={6} />
          <Text softWrap={false} style={mutedSmall}>
            {music_box_display.musicBoxFormatDuration(progress.durationMs)}
          </Text>
        </Row>
      </_MusicBoxScaleDown>
    </Column>
  );
}

/** Port of the Dart `_DotProgressPainter` as SVG over the measured width. */
function _DotProgressPainter({ fraction, active }: { fraction: number; active: boolean }) {
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  const width = size?.width ?? 0;
  const height = size?.height ?? 6;
  const step = 5;
  const radius = 1.3;
  const count = Math.max(2, Math.floor(width / step));
  const spacing = (width - radius * 2) / (count - 1);
  const lit = active ? Math.round(Math.min(Math.max(fraction, 0), 1) * (count - 1)) : -1;
  const y = height / 2;
  const off = argb(0xff2a2a30);
  const on = UiColors.accent;
  const dots: ReactNode[] = [];
  if (width > 0) {
    for (let i = 0; i < count; i++) {
      const x = radius + i * spacing;
      if (i === lit) {
        dots.push(<circle key={`${i}-halo`} cx={x} cy={y} r={radius * 2.6} fill={withAlpha(UiColors.accent, 0.25)} />);
        dots.push(<circle key={i} cx={x} cy={y} r={radius * 1.6} fill={on} />);
      } else {
        dots.push(<circle key={i} cx={x} cy={y} r={radius} fill={i < lit ? on : off} />);
      }
    }
  }
  return (
    <div ref={ref} style={{ position: 'absolute', inset: 0 }}>
      {width > 0 ? (
        // CustomPaint does not clip: the lit dot's halo overhangs the 6px box.
        <svg width={width} height={height} aria-hidden="true" style={{ display: 'block', overflow: 'visible' }}>
          {dots}
        </svg>
      ) : null}
    </div>
  );
}
