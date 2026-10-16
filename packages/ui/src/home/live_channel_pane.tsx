/**
 * Port of `lib/src/home/live_channel_pane.dart` (part-library root).
 *
 * The Dart library's parts are separate modules here:
 *  - `live_channel_members` — header, member stage and member cards;
 *  - `live_channel_media` — focused media stage, full-screen stage, track helpers;
 *  - `live_channel_controls` — control bar, hover info / hover volume, inline music box;
 *  - `live_channel_music_box*` — the docked music box console.
 *
 * NOTE: the part modules import each other (and this root) in a cycle, like
 * the Dart parts shared one library scope. Values imported across the cycle
 * are therefore only read at render / call time, never while a module is
 * being evaluated.
 */
import { useEffect, useLayoutEffect, useState, type CSSProperties, type ReactNode, type RefObject } from 'react';

import * as live_display from '@gang-chat/core/app/live_display';
import type { MusicBoxController } from '@gang-chat/core/app/music_box_controller';
import type { LiveVideoTrack } from '@gang-chat/core/live/live_session';
import {
  musicBoxBotIdentity,
  type CurrentUser,
  type LiveParticipant,
  type LiveState,
  type MusicBoxPlaybackMode,
  type MusicBoxQueueItem,
  type MusicBoxSearchResult,
  type MusicBoxState,
  type PublicRoom,
  type UserSummary,
} from '@gang-chat/core/protocol/models';

import { Column, Expanded, SizedBox } from '../lib/layout';
import type { TextEditingController } from '../lib/text_editing';
import { useViewState, ViewState } from '../lib/view_state';
import { insertOverlay, rectInWindow, type OverlayEntryHandle } from '../ui/overlay';
import { UiColors, UiRadii, argb } from '../ui/tokens';
import { _LiveControlBar } from './live_channel_controls';
import { _LiveMediaStage, _resolveLiveStageTrack, liveStageTrackLabel } from './live_channel_media';
import { _LiveMemberStage, _LiveRoomHeader } from './live_channel_members';
import { LiveMusicBoxPanel, _musicBoxMinComfortableHeight } from './live_channel_music_box';
import type { MusicPlaylistEditCallback } from './music_playlist_profile_card';
import type { RoomProfileResolver, UserProfileActionBuilder, UserProfileResolver } from './room_profile_card';

// The Dart library's public API also includes the public symbols declared in
// its parts; re-export them so `import … from './live_channel_pane'` keeps
// working the way `import 'live_channel_pane.dart'` did.
export { LiveFullScreenStage, liveStageTrackLabel, resolveLiveStageTrackForTest } from './live_channel_media';
export type { LiveFullScreenStageProps } from './live_channel_media';
export { LiveMusicBoxPanel } from './live_channel_music_box';

export const _paneEdgeInset = 14.0;
export const _paneTopInset = _paneEdgeInset;
export const _liveRoomRadius = UiRadii.md;
export const _liveRoomPadding = 18.0;
// Width of the right-docked music box panel when expanded. Wide enough for a
// song row to carry its index, title, requester, and two actions.
export const _musicBoxPanelWidth = 340.0;
export const _memberCardWidth = 154.0;
export const _memberCardMinTwoColumnWidth = 135.0;
export const _memberCardSpacing = 12.0;
export const _memberCardSurfaceHeight = _memberCardWidth;
export const _controlButtonSize = 44.0;
export const _controlHoverInfoBelowReserve = 4.0;
export const _controlHoverInfoVerticalOffset = 24.0;
export const _liveStageControlGap = 16.0;
// The open music box always has its 44px inline player above one 44px
// transport row in the standard layout. Narrow layouts may add more rows, but
// those rows must move the docked panel upward instead of shortening it.
export const _musicBoxStandardControlHeight = _controlButtonSize * 2 + 10.0;
export const _liveRoomBackground = UiColors.surfaceLow;
export const _liveRoomBorder = UiColors.border;
export const _memberSpeakingBackground = argb(0xff252a34);
export const _memberIdleBackground = argb(0xff1d2328);

export enum LiveStageSelectionMode {
  none = 'none',
  track = 'track',
}

export class LiveStageSelection {
  private constructor(
    readonly mode: LiveStageSelectionMode,
    readonly identity: string | null,
    readonly isScreenShare: boolean | null,
  ) {}

  /** Dart `const LiveStageSelection.track(identity:, isScreenShare:)`. */
  static track(opts: { identity: string; isScreenShare: boolean }): LiveStageSelection {
    return new LiveStageSelection(LiveStageSelectionMode.track, opts.identity, opts.isScreenShare);
  }

  /** Dart `const LiveStageSelection.none()`. */
  static none(): LiveStageSelection {
    return new LiveStageSelection(LiveStageSelectionMode.none, null, null);
  }

  static fromTrack(track: LiveVideoTrack): LiveStageSelection {
    return LiveStageSelection.track({
      identity: track.identity,
      isScreenShare: track.isScreenShare,
    });
  }
}

export interface LiveChannelPaneProps {
  title: string;
  avatarUrl: string | null;
  live: LiveState | null;
  currentUser: CurrentUser;
  loading: boolean;
  joined: boolean;
  joining: boolean;
  leaving: boolean;
  micMuted: boolean;
  headphonesMuted: boolean;
  voiceBlocked: boolean;
  cameraOn: boolean;
  screenSharing: boolean;
  speakingUserIds: ReadonlySet<string>;
  connectedParticipantIds: ReadonlySet<string>;
  liveKitMicMutedByParticipantId: ReadonlyMap<string, boolean>;
  videoTracks: readonly LiveVideoTrack[];
  stageSelection: LiveStageSelection | null;

  /**
   * Keeps the stage chrome mounted while releasing its native video texture.
   *
   * Windows uses this while the same track is rendered by the full-screen
   * overlay. Keeping both renderers alive doubles the per-frame ARGB copy and
   * leaves two native texture consumers attached across display transitions.
   */
  suspendStageVideo?: boolean;
  onStageSelectionChanged: (selection: LiveStageSelection | null) => void;
  onEnterFullScreen: (track: LiveVideoTrack) => void;
  onBackToChat: () => void;
  onJoin: () => void;
  onLeave: () => void;
  onToggleMic: (() => void) | null;
  onToggleHeadphones: () => void;
  onToggleCamera: () => void;
  onFlipCamera: (() => Promise<boolean>) | null;
  onSetLocalCameraMirrored: (mirrored: boolean) => Promise<boolean>;
  onToggleShare: (() => void) | null;
  musicBox: MusicBoxState | null;
  musicBoxOpen: boolean;
  musicBoxSearchController: TextEditingController;
  musicBoxSearchResults: MusicBoxSearchResult[];
  musicBoxSearching: boolean;
  musicBoxSearchError: string | null;
  musicBoxSource: string;
  onToggleMusicBox: () => void;
  onMusicBoxTogglePlayback: () => void;
  onMusicBoxSkip: () => void;
  onMusicBoxPrevious?: (() => void) | null;
  onMusicBoxModeChanged?: ((mode: MusicBoxPlaybackMode) => void) | null;
  musicBoxController?: MusicBoxController | null;
  musicBoxRoomId?: string | null;
  musicPlaylistsRevision?: number;
  musicBoxRoom?: PublicRoom | null;
  onMusicBoxStateChanged?: ((state: MusicBoxState) => void) | null;
  onCreateFirstRoomMusicPlaylist?: (() => void) | null;
  onCreateFirstPersonalMusicPlaylist?: (() => void) | null;
  onEditRoomMusicPlaylist?: MusicPlaylistEditCallback | null;
  onEditPersonalMusicPlaylist?: MusicPlaylistEditCallback | null;
  onMusicBoxQueueResult: (result: MusicBoxSearchResult) => void;
  onMusicBoxRemoveItem: (item: MusicBoxQueueItem) => void;
  onMusicBoxSourceChanged: (source: string) => void;

  /**
   * Local microphone input preference (0-1); 0 mutes the sent mic without
   * writing system input gain.
   */
  inputVolume: number;

  /** Local voice listening volume (0-1), applied to ordinary remote speakers. */
  outputVolume: number;

  /**
   * Local listening volume for the music box bot (0–1), restored from the
   * store and persisted by [onMusicBoxVolumeChanged].
   */
  musicBoxVolume: number;

  /**
   * Local listening volume for remote screen-share audio (0-1), separate from
   * ordinary voice output.
   */
  screenShareVolume: number;

  /**
   * Per-user relative voice volume (0-2) for ordinary remote speaker audio.
   * Screen-share audio remains controlled by [screenShareVolume].
   */
  participantVoiceVolume: (userId: string) => number;

  onInputVolumeChanged: (volume: number) => void;
  onOutputVolumeChanged: (volume: number) => void;
  onMusicBoxVolumeChanged: (volume: number) => void;
  onScreenShareVolumeChanged: (volume: number) => void;
  onScreenShareMuteToggled: () => void;
  onParticipantVoiceVolumeChanged: (userId: string, volume: number) => void;
  onParticipantVoiceMuteToggled: (userId: string) => void;
  canModerateParticipant: (participant: LiveParticipant) => boolean;
  onToggleParticipantMicModeration: (participant: LiveParticipant) => void;
  onToggleParticipantHeadphonesModeration: (participant: LiveParticipant) => void;
  canRemoveParticipant: (participant: LiveParticipant) => boolean;
  onRemoveParticipant: (participant: LiveParticipant) => void;
  onResolveParticipantProfile?: UserProfileResolver | null;
  onResolveParticipantRoomProfile?: RoomProfileResolver | null;
  onEnterParticipantProfileRoom?: ((room: PublicRoom) => void) | null;
  participantProfileActionBuilder?: UserProfileActionBuilder | null;
}

export class _LiveChannelPaneState extends ViewState<LiveChannelPaneProps> {
  _resolveStageTrack(): LiveVideoTrack | null {
    return _resolveLiveStageTrack({
      tracks: this.widget.videoTracks,
      selection: this.widget.stageSelection,
    });
  }

  _selectStage = (track: LiveVideoTrack): void => {
    this._selectStageAndJoinIfNeeded(LiveStageSelection.fromTrack(track));
  };

  _selectScreenShareStage = (identity: string): void => {
    this._selectStageAndJoinIfNeeded(LiveStageSelection.track({ identity, isScreenShare: true }));
  };

  _selectCameraStage = (identity: string): void => {
    this._selectStageAndJoinIfNeeded(LiveStageSelection.track({ identity, isScreenShare: false }));
  };

  _selectStageAndJoinIfNeeded(selection: LiveStageSelection): void {
    const widget = this.widget;
    widget.onStageSelectionChanged(selection);
    if (!widget.joined && !widget.joining && !widget.leaving && !widget.loading) {
      widget.onJoin();
    }
  }

  _exitStage = (): void => {
    this.widget.onStageSelectionChanged(LiveStageSelection.none());
  };
}

export function LiveChannelPane(props: LiveChannelPaneProps) {
  const state = useViewState(() => new _LiveChannelPaneState(), props);
  const widget = props;
  const participants = live_display
    .visibleLiveParticipantsForStage(widget.live?.participants ?? [], {
      currentUserId: widget.currentUser.id,
      localParticipantReady: widget.joined && !widget.joining,
      connectedParticipantIds: widget.connectedParticipantIds,
    })
    .filter((p) => p.user.id !== musicBoxBotIdentity);
  const stageTrack = state._resolveStageTrack();
  const musicBox = widget.musicBox;
  const musicBoxEnabled = musicBox?.enabled ?? false;
  const musicBoxOpen = widget.joined && musicBoxEnabled && widget.musicBoxOpen;

  return (
    <div
      style={{
        flex: '1 1 0',
        alignSelf: 'stretch',
        width: '100%',
        height: '100%',
        minWidth: 0,
        minHeight: 0,
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: UiColors.background,
        padding: `${_paneTopInset}px ${_paneEdgeInset}px ${_paneEdgeInset}px ${_paneEdgeInset}px`,
      }}
    >
      <div
        style={{
          flex: '1 1 0',
          minHeight: 0,
          minWidth: 0,
          boxSizing: 'border-box',
          display: 'flex',
          flexDirection: 'column',
          backgroundColor: _liveRoomBackground,
          borderRadius: _liveRoomRadius,
          border: `1px solid ${_liveRoomBorder}`,
          boxShadow: `0px 10px 22px 0px ${argb(0x66080a0d)}`,
          // Flutter's DecoratedBox border does not inset the child; the
          // Padding below it does. Subtract the 1px border so the content
          // keeps its 18px inset from the outer edge.
          padding: _liveRoomPadding - 1,
        }}
      >
        <Column crossAxisAlignment="stretch" style={{ flex: '1 1 0', minHeight: 0 }}>
          <_LiveRoomHeader title={widget.title} />
          <SizedBox height={16} />
          <Expanded>
            <_LiveChannelBodyLayout
              stage={
                // PORT: the Dart Column centres its children horizontally, but
                // both children fill the width (StackFit.expand / Align), so
                // stretching is the same layout.
                <Column crossAxisAlignment="stretch" style={{ flex: '1 1 0', minHeight: 0 }}>
                  {stageTrack != null ? (
                    <>
                      <Expanded flex={3}>
                        <_LiveMediaStage
                          track={stageTrack}
                          renderVideo={!(widget.suspendStageVideo ?? false)}
                          label={liveStageTrackLabel(widget.live, stageTrack)}
                          screenShareViewers={
                            stageTrack.isScreenShare
                              ? live_display.liveScreenShareViewers(widget.live, stageTrack.identity)
                              : ([] as UserSummary[])
                          }
                          screenShareVolume={widget.screenShareVolume}
                          cameraMirrored={
                            live_display.liveParticipantByUserId(widget.live, stageTrack.identity)?.cameraMirrored ?? false
                          }
                          onFlipCamera={stageTrack.isLocal ? widget.onFlipCamera : null}
                          onSetLocalCameraMirrored={widget.onSetLocalCameraMirrored}
                          onExit={state._exitStage}
                          onFullScreen={() => widget.onEnterFullScreen(stageTrack)}
                          onScreenShareVolumeChanged={widget.onScreenShareVolumeChanged}
                          onScreenShareMuteToggled={widget.onScreenShareMuteToggled}
                        />
                      </Expanded>
                      <SizedBox height={14} />
                    </>
                  ) : null}
                  <Expanded flex={stageTrack == null ? 1 : 2}>
                    <_LiveMemberStage
                      loading={widget.loading}
                      participants={participants}
                      currentUser={widget.currentUser}
                      localMicMuted={widget.micMuted}
                      localHeadphonesMuted={widget.headphonesMuted}
                      speakingUserIds={widget.speakingUserIds}
                      liveKitMicMutedByParticipantId={widget.liveKitMicMutedByParticipantId}
                      videoTracks={widget.videoTracks}
                      stageTrack={stageTrack}
                      onSelectStage={state._selectStage}
                      onSelectScreenShareStage={state._selectScreenShareStage}
                      onSelectCameraStage={state._selectCameraStage}
                      onToggleMic={widget.onToggleMic}
                      onToggleHeadphones={widget.onToggleHeadphones}
                      participantVoiceVolume={widget.participantVoiceVolume}
                      onParticipantVoiceVolumeChanged={widget.onParticipantVoiceVolumeChanged}
                      onParticipantVoiceMuteToggled={widget.onParticipantVoiceMuteToggled}
                      canModerateParticipant={widget.canModerateParticipant}
                      onToggleParticipantMicModeration={widget.onToggleParticipantMicModeration}
                      onToggleParticipantHeadphonesModeration={widget.onToggleParticipantHeadphonesModeration}
                      canRemoveParticipant={widget.canRemoveParticipant}
                      onRemoveParticipant={widget.onRemoveParticipant}
                      onResolveParticipantProfile={widget.onResolveParticipantProfile}
                      onResolveParticipantRoomProfile={widget.onResolveParticipantRoomProfile}
                      onEnterParticipantProfileRoom={widget.onEnterParticipantProfileRoom}
                      participantProfileActionBuilder={widget.participantProfileActionBuilder}
                    />
                  </Expanded>
                </Column>
              }
              controls={
                <_LiveControlBar
                  joined={widget.joined}
                  joining={widget.joining}
                  micMuted={widget.micMuted}
                  headphonesMuted={widget.headphonesMuted}
                  voiceBlocked={widget.voiceBlocked}
                  cameraOn={widget.cameraOn}
                  screenSharing={widget.screenSharing}
                  inputVolume={widget.inputVolume}
                  outputVolume={widget.outputVolume}
                  musicBox={musicBox}
                  musicBoxEnabled={musicBoxEnabled}
                  musicBoxOpen={musicBoxOpen}
                  onJoin={widget.loading || widget.leaving ? null : widget.onJoin}
                  onLeave={widget.onLeave}
                  onToggleMic={widget.onToggleMic}
                  onToggleHeadphones={widget.onToggleHeadphones}
                  onToggleCamera={widget.onToggleCamera}
                  onToggleShare={widget.onToggleShare}
                  onInputVolumeChanged={widget.onInputVolumeChanged}
                  onOutputVolumeChanged={widget.onOutputVolumeChanged}
                  onToggleMusicBox={widget.onToggleMusicBox}
                  onMusicBoxTogglePlayback={widget.onMusicBoxTogglePlayback}
                  onMusicBoxSkip={widget.onMusicBoxSkip}
                  musicBoxController={widget.musicBoxController ?? null}
                  musicBoxRoomId={widget.musicBoxRoomId ?? null}
                  onMusicBoxStateChanged={widget.onMusicBoxStateChanged ?? null}
                  currentUser={widget.currentUser}
                  onResolveUserProfile={widget.onResolveParticipantProfile ?? null}
                  onResolveRoomProfile={widget.onResolveParticipantRoomProfile ?? null}
                  onEnterCommonRoom={widget.onEnterParticipantProfileRoom ?? null}
                  userProfileActionBuilder={widget.participantProfileActionBuilder ?? null}
                  onCollapse={widget.onBackToChat}
                />
              }
              musicBox={
                musicBoxOpen && musicBox != null ? (
                  <LiveMusicBoxPanel
                    key="live-music-box-panel"
                    state={musicBox}
                    searchController={widget.musicBoxSearchController}
                    searchResults={widget.musicBoxSearchResults}
                    searching={widget.musicBoxSearching}
                    searchError={widget.musicBoxSearchError}
                    source={widget.musicBoxSource}
                    onTogglePlayback={widget.onMusicBoxTogglePlayback}
                    onSkip={widget.onMusicBoxSkip}
                    onPrevious={widget.onMusicBoxPrevious ?? undefined}
                    onModeChanged={widget.onMusicBoxModeChanged ?? undefined}
                    controller={widget.musicBoxController ?? undefined}
                    roomId={widget.musicBoxRoomId ?? undefined}
                    playlistsRevision={widget.musicPlaylistsRevision ?? 0}
                    room={widget.musicBoxRoom ?? undefined}
                    onStateChanged={widget.onMusicBoxStateChanged ?? undefined}
                    currentUser={widget.currentUser}
                    onResolveUserProfile={widget.onResolveParticipantProfile ?? undefined}
                    onResolveRoomProfile={widget.onResolveParticipantRoomProfile ?? undefined}
                    onEnterCommonRoom={widget.onEnterParticipantProfileRoom ?? undefined}
                    userProfileActionBuilder={widget.participantProfileActionBuilder ?? undefined}
                    onCreateFirstRoomPlaylist={widget.onCreateFirstRoomMusicPlaylist ?? undefined}
                    onCreateFirstPersonalPlaylist={widget.onCreateFirstPersonalMusicPlaylist ?? undefined}
                    onEditRoomPlaylist={widget.onEditRoomMusicPlaylist ?? undefined}
                    onEditPersonalPlaylist={widget.onEditPersonalMusicPlaylist ?? undefined}
                    onQueueResult={widget.onMusicBoxQueueResult}
                    onRemoveItem={widget.onMusicBoxRemoveItem}
                    onSourceChanged={widget.onMusicBoxSourceChanged}
                    onClose={widget.onToggleMusicBox}
                    volume={widget.musicBoxVolume}
                    onVolumeChanged={widget.onMusicBoxVolumeChanged}
                  />
                ) : null
              }
            />
          </Expanded>
        </Column>
      </div>
    </div>
  );
}

interface _LiveChannelBodyLayoutProps {
  stage: ReactNode;
  controls: ReactNode;
  musicBox: ReactNode | null;
}

/**
 * Lays out the live stage and its controls in one pass so the docked music box
 * can keep a stable height. Extra compact control rows reduce the stage and
 * move the panel upward; they never compress the panel itself.
 *
 * PORT: the `CustomMultiChildLayout` becomes a flex column — the controls
 * take their natural height and the stage fills what is left after the
 * 16px gap and the 4px hover-info reserve, which is exactly what the delegate
 * computed. The music box anchor is a zero-size box pinned to the stage's
 * bottom-right corner (`Offset(size.width, stageHeight)`), and the
 * `OverlayPortal` is an overlay entry positioned from both measured rects.
 */
export class _LiveChannelBodyLayoutState extends ViewState<_LiveChannelBodyLayoutProps> {
  readonly _bodyKey: RefObject<HTMLDivElement | null> = { current: null };
  readonly _musicBoxAnchorKey: RefObject<HTMLDivElement | null> = { current: null };
  _musicBoxPortal: OverlayEntryHandle | null = null;

  override dispose(): void {
    this._musicBoxPortal?.remove();
    this._musicBoxPortal = null;
  }

  /** Runs after every commit (Dart's post-frame `_schedulePortalSync`). */
  _syncPortal(): void {
    if (!this.mounted) return;
    if (this.widget.musicBox != null) {
      if (this._musicBoxPortal == null) {
        this._musicBoxPortal = insertOverlay(() => (
          <_LiveChannelMusicBoxOverlay
            bodyKey={this._bodyKey}
            musicBoxAnchorKey={this._musicBoxAnchorKey}
            musicBox={this.widget.musicBox}
          />
        ));
      } else {
        // The overlay content follows this widget's rebuilds (OverlayPortal).
        this._musicBoxPortal.markNeedsBuild();
      }
    } else if (this._musicBoxPortal != null) {
      this._musicBoxPortal.remove();
      this._musicBoxPortal = null;
    }
  }
}

export function _LiveChannelBodyLayout(props: _LiveChannelBodyLayoutProps) {
  const state = useViewState(() => new _LiveChannelBodyLayoutState(), props);

  useLayoutEffect(() => {
    state._syncPortal();
  });

  return (
    <div
      ref={(node) => {
        state._bodyKey.current = node;
      }}
      style={{ flex: '1 1 0', minHeight: 0, minWidth: 0, display: 'flex', flexDirection: 'column' }}
    >
      <div style={{ position: 'relative', flex: '1 1 0', minHeight: 0, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        {props.stage}
        <div
          ref={(node) => {
            state._musicBoxAnchorKey.current = node;
          }}
          aria-hidden="true"
          style={{ position: 'absolute', right: 0, bottom: 0, width: 0, height: 0, pointerEvents: 'none' }}
        />
      </div>
      <div style={{ height: _liveStageControlGap, flexShrink: 0 }} />
      <div style={{ flexShrink: 0, minWidth: 0 }}>{props.controls}</div>
      <div style={{ height: _controlHoverInfoBelowReserve, flexShrink: 0 }} />
    </div>
  );
}

/** The `overlayChildBuilder` of the Dart `OverlayPortal`. */
function _LiveChannelMusicBoxOverlay({
  bodyKey,
  musicBoxAnchorKey,
  musicBox,
}: {
  bodyKey: RefObject<HTMLDivElement | null>;
  musicBoxAnchorKey: RefObject<HTMLDivElement | null>;
  musicBox: ReactNode | null;
}) {
  // LayoutBuilder: re-run the placement whenever the body or the window
  // changes size (the Dart builder re-ran on every overlay layout).
  const [, setTick] = useState(0);
  useEffect(() => {
    const relayout = () => setTick((tick) => tick + 1);
    window.addEventListener('resize', relayout);
    let observer: ResizeObserver | null = null;
    if (typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(relayout);
      if (bodyKey.current != null) observer.observe(bodyKey.current);
      const stage = musicBoxAnchorKey.current?.parentElement;
      if (stage != null) observer.observe(stage);
    }
    return () => {
      window.removeEventListener('resize', relayout);
      observer?.disconnect();
    };
  }, [bodyKey, musicBoxAnchorKey]);

  const bodyRect = rectInWindow(bodyKey);
  const anchorRect = rectInWindow(musicBoxAnchorKey);
  if (bodyRect == null || anchorRect == null || musicBox == null) {
    return null;
  }
  const anchorTop = anchorRect.y;
  const anchorRight = anchorRect.x + anchorRect.width;
  const standardPanelHeight =
    bodyRect.height - _musicBoxStandardControlHeight - _liveStageControlGap - _controlHoverInfoBelowReserve;
  const desiredPanelHeight =
    standardPanelHeight < _musicBoxMinComfortableHeight ? _musicBoxMinComfortableHeight : standardPanelHeight;
  const panelHeight = desiredPanelHeight > anchorTop ? anchorTop : desiredPanelHeight;
  const panelWidth = bodyRect.width < _musicBoxPanelWidth ? bodyRect.width : _musicBoxPanelWidth;
  if (panelHeight <= 0 || panelWidth <= 0) {
    return null;
  }
  const style: CSSProperties = {
    position: 'absolute',
    left: anchorRight - panelWidth,
    top: anchorTop - panelHeight,
    width: panelWidth,
    height: panelHeight,
    display: 'flex',
    flexDirection: 'column',
    pointerEvents: 'auto',
  };
  return <div style={style}>{musicBox}</div>;
}
