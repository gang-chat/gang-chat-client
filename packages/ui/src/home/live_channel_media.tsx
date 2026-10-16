/**
 * Port of `lib/src/home/live_channel_media.dart` (part of `live_channel_pane`).
 */
import { useState, type CSSProperties, type KeyboardEvent as ReactKeyboardEvent, type ReactNode, type RefObject } from 'react';

import { normalizedAudioVolume } from '@gang-chat/core/app/audio_levels';
import * as live_display from '@gang-chat/core/app/live_display';
import * as room_display from '@gang-chat/core/app/room_display';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { Timer, unawaited } from '@gang-chat/core/lib/async';
import type { LiveVideoTrack } from '@gang-chat/core/live/live_session';
import type { LiveState, UserSummary } from '@gang-chat/core/protocol/models';
import { FullScreenSystemUiController } from '@gang-chat/core/shell/full_screen_system_ui_controller';

import { Row, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { useViewState, ViewState } from '../lib/view_state';
import { LiveVideoTrackFit, LiveVideoTrackView } from '../live/live_video_track_view';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { Icon, Icons, type IconData } from '../ui/icon';
import { UiColors, UiRadii, UiTypography, withAlpha } from '../ui/tokens';
import { _HoverInfo, _HoverVolumeButton, _hoverVolumePanelHeight, _volumeLevelIcon } from './live_channel_controls';
import { _controlButtonSize, LiveStageSelectionMode, type LiveStageSelection } from './live_channel_pane';

enum _LiveMediaKind {
  camera = 'camera',
  screenShare = 'screenShare',
}

const _fill: CSSProperties = { position: 'absolute', inset: 0 };

export function _LiveMediaVideo({
  track,
  fit,
  mirrored = false,
}: {
  track: LiveVideoTrack;
  fit?: LiveVideoTrackFit | null;
  mirrored?: boolean;
}) {
  return (
    <LiveVideoTrackView
      track={track}
      fit={fit ?? (track.isScreenShare ? LiveVideoTrackFit.contain : LiveVideoTrackFit.cover)}
      mirrored={mirrored}
    />
  );
}

export interface _LiveMediaStageProps {
  track: LiveVideoTrack;
  renderVideo: boolean;
  label: string;
  screenShareViewers: readonly UserSummary[];
  screenShareVolume: number;
  cameraMirrored: boolean;
  onFlipCamera: (() => Promise<boolean>) | null;
  onSetLocalCameraMirrored: (mirrored: boolean) => Promise<boolean>;
  onExit: () => void;
  onFullScreen: () => void;
  onScreenShareVolumeChanged: (volume: number) => void;
  onScreenShareMuteToggled: () => void;
}

export class _LiveMediaStageState extends ViewState<_LiveMediaStageProps> {
  _viewerMirrorOverride: boolean | null = null;
  _pendingSharedMirror: boolean | null = null;
  _flippingCamera = false;
  _settingSharedMirror = false;

  get _cameraMirrored(): boolean {
    if (this.widget.track.isLocal) {
      return this._pendingSharedMirror ?? this.widget.cameraMirrored;
    }
    return this._viewerMirrorOverride ?? this.widget.cameraMirrored;
  }

  override didUpdateProps(oldWidget: _LiveMediaStageProps): void {
    const widget = this.widget;
    const trackChanged =
      oldWidget.track.identity !== widget.track.identity ||
      oldWidget.track.isScreenShare !== widget.track.isScreenShare ||
      oldWidget.track.isLocal !== widget.track.isLocal;
    // PORT: didUpdateProps runs after the render, so field changes re-render.
    if (trackChanged) {
      this.setState(() => {
        this._viewerMirrorOverride = null;
        this._pendingSharedMirror = null;
        this._flippingCamera = false;
        this._settingSharedMirror = false;
      });
      return;
    }
    if (this._pendingSharedMirror === widget.cameraMirrored) {
      this.setState(() => {
        this._pendingSharedMirror = null;
      });
    }
  }

  _flipCamera = async (): Promise<void> => {
    const callback = this.widget.onFlipCamera;
    if (callback == null || this._flippingCamera) return;
    this.setState(() => {
      this._flippingCamera = true;
    });
    await callback();
    if (this.mounted) {
      this.setState(() => {
        this._flippingCamera = false;
      });
    }
  };

  _toggleMirror = async (): Promise<void> => {
    const mirrored = !this._cameraMirrored;
    if (!this.widget.track.isLocal) {
      this.setState(() => {
        this._viewerMirrorOverride = mirrored;
      });
      return;
    }
    if (this._settingSharedMirror) return;
    this.setState(() => {
      this._settingSharedMirror = true;
      this._pendingSharedMirror = mirrored;
    });
    const updated = await this.widget.onSetLocalCameraMirrored(mirrored);
    if (!this.mounted) return;
    this.setState(() => {
      this._settingSharedMirror = false;
      if (!updated) this._pendingSharedMirror = null;
    });
  };
}

export function _LiveMediaStage(props: _LiveMediaStageProps) {
  const state = useViewState(() => new _LiveMediaStageState(), props);
  const widget = props;
  const track = widget.track;
  const isRemoteScreenShare = track.isScreenShare && !track.isLocal;
  const cameraMirrored = state._cameraMirrored;
  return (
    <div
      style={{
        position: 'relative',
        flex: '1 1 0',
        minHeight: 0,
        minWidth: 0,
        backgroundColor: UiColors.surfacePressed,
        // Camera stages are clipped to rounded corners; screen shares keep
        // their square edges.
        borderRadius: track.isScreenShare ? undefined : UiRadii.lg,
        overflow: 'hidden',
      }}
    >
      {widget.renderVideo ? (
        <div style={_fill}>
          <_LiveMediaVideo track={track} fit={LiveVideoTrackFit.contain} mirrored={cameraMirrored} />
        </div>
      ) : (
        <div data-testid="live-stage:video-suspended" style={_fill} />
      )}
      <div style={{ position: 'absolute', left: 0, top: 0, maxWidth: '100%' }}>
        <_LiveStageBadge label={widget.label} kind={track.isScreenShare ? _LiveMediaKind.screenShare : _LiveMediaKind.camera} />
      </div>
      {track.isScreenShare && widget.screenShareViewers.length > 0 ? (
        <div style={{ position: 'absolute', left: 8, bottom: 8 }}>
          <_ScreenShareViewerPreview viewers={widget.screenShareViewers} />
        </div>
      ) : null}
      <div style={{ position: 'absolute', top: 8, right: 8 }}>
        <Row mainAxisSize="min">
          <_StageOverlayIconButton
            data-testid="live-stage:exit"
            icon={Icons.close_fullscreen}
            infoMessage="退出焦点画面"
            onPressed={widget.onExit}
          />
          <SizedBox width={6} />
          <_StageOverlayIconButton
            data-testid="live-stage:fullscreen"
            icon={Icons.fullscreen}
            infoMessage="全屏查看"
            onPressed={widget.onFullScreen}
          />
        </Row>
      </div>
      {isRemoteScreenShare ? (
        <div style={{ position: 'absolute', right: 8, bottom: 8 }}>
          <_StageScreenShareVolumeButton
            value={widget.screenShareVolume}
            onChanged={widget.onScreenShareVolumeChanged}
            onPressed={widget.onScreenShareMuteToggled}
          />
        </div>
      ) : null}
      {!track.isScreenShare ? (
        <div style={{ position: 'absolute', right: 8, bottom: 8 }}>
          <Row mainAxisSize="min">
            {track.isLocal && widget.onFlipCamera != null ? (
              <>
                <_StageOverlayIconButton
                  data-testid="live-stage:flip-camera"
                  icon={Icons.flip_camera_android}
                  infoMessage="翻转摄像头"
                  onPressed={state._flippingCamera ? null : () => unawaited(state._flipCamera())}
                />
                <SizedBox width={6} />
              </>
            ) : null}
            <_StageOverlayIconButton
              data-testid="live-stage:mirror-camera"
              icon={Icons.flip}
              infoMessage={cameraMirrored ? '取消镜像' : '镜像'}
              active={cameraMirrored}
              onPressed={state._settingSharedMirror ? null : () => unawaited(state._toggleMirror())}
            />
          </Row>
        </div>
      ) : null}
    </div>
  );
}

function _StageScreenShareVolumeButton({
  value,
  onChanged,
  onPressed,
}: {
  value: number;
  onChanged: (value: number) => void;
  onPressed: () => void;
}) {
  const normalized = normalizedAudioVolume(value);
  const message = normalized <= 0 ? '取消静音共享屏幕' : '静音共享屏幕';
  return (
    <_HoverVolumeButton
      data-testid="live-stage:screen-share-volume"
      value={normalized}
      semanticLabel="共享屏幕输出音量"
      infoMessage={message}
      onChanged={onChanged}
      panelWidth={34}
      panelHeight={(_hoverVolumePanelHeight * 34) / _controlButtonSize}
    >
      <div role="button" aria-label={message} onClick={onPressed} style={{ display: 'flex' }}>
        <_StageOverlayIconSurface icon={_screenShareVolumeIcon(normalized)} />
      </div>
    </_HoverVolumeButton>
  );
}

export interface LiveFullScreenStageProps {
  track: LiveVideoTrack;
  label: string;
  cameraMirrored?: boolean;
  screenShareViewers?: readonly UserSummary[];
  screenShareVolume: number;
  onScreenShareVolumeChanged: (volume: number) => void;
  onScreenShareMuteToggled: () => void;
  onExit: () => void;
  systemUiController?: FullScreenSystemUiController | null;
}

export class _LiveFullScreenStageState extends ViewState<LiveFullScreenStageProps> {
  static readonly _controlsAutoHideDelay = 3000;

  readonly _focusNode: RefObject<HTMLDivElement | null> = { current: null };
  _controlsHideTimer: Timer | null = null;
  _viewportSize: { width: number; height: number } | null = null;
  _systemUiController!: FullScreenSystemUiController;
  _controlsTimerGeneration = 0;
  _controlsVisible = true;
  private _removeResizeListener: (() => void) | null = null;

  override initState(): void {
    this._systemUiController = this.widget.systemUiController ?? new FullScreenSystemUiController();
    unawaited(this._ignoreSystemUiFailure(this._systemUiController.setControlsVisible(true)));
    this._scheduleControlsAutoHide();
    // Post-frame focus request (KeyboardListener autofocus).
    requestAnimationFrame(() => {
      if (this.mounted) this._focusNode.current?.focus({ preventScroll: true });
    });
    this._didChangeDependencies();
    const onResize = () => this._didChangeDependencies();
    window.addEventListener('resize', onResize);
    this._removeResizeListener = () => window.removeEventListener('resize', onResize);
  }

  override didUpdateProps(oldWidget: LiveFullScreenStageProps): void {
    const widget = this.widget;
    if (oldWidget.systemUiController !== widget.systemUiController) {
      unawaited(this._ignoreSystemUiFailure(this._systemUiController.restore()));
      this._systemUiController = widget.systemUiController ?? new FullScreenSystemUiController();
      unawaited(this._ignoreSystemUiFailure(this._systemUiController.setControlsVisible(this._controlsVisible)));
    }
    if (oldWidget.track.identity !== widget.track.identity || oldWidget.track.isScreenShare !== widget.track.isScreenShare) {
      this._showControlsAndRestartTimer();
    }
  }

  /**
   * Port of `didChangeDependencies` reacting to `MediaQuery.sizeOf` /
   * `orientationOf`: the DOM equivalent is the window `resize` event.
   */
  _didChangeDependencies(): void {
    const nextViewportSize = { width: window.innerWidth, height: window.innerHeight };
    const isAndroid = HostPlatform.isAndroid;
    const previous = this._viewportSize;
    // PORT: Android compared orientation; the DOM reads it from the size.
    const orientation = (size: { width: number; height: number }) => (size.width > size.height ? 'landscape' : 'portrait');
    const viewportChanged = isAndroid
      ? previous != null && orientation(previous) !== orientation(nextViewportSize)
      : previous != null && (previous.width !== nextViewportSize.width || previous.height !== nextViewportSize.height);
    this._viewportSize = nextViewportSize;
    if (viewportChanged) {
      // A rotation or full-screen resize is itself a user-visible transition.
      this.setState(() => {
        this._controlsVisible = true;
      });
      this._scheduleControlsAutoHide();
    }
  }

  _scheduleControlsAutoHide(): void {
    const generation = ++this._controlsTimerGeneration;
    this._controlsHideTimer?.cancel();
    this._controlsHideTimer = new Timer(_LiveFullScreenStageState._controlsAutoHideDelay, () => {
      if (!this.mounted || generation !== this._controlsTimerGeneration || !this._controlsVisible) {
        return;
      }
      this.setState(() => {
        this._controlsVisible = false;
      });
      unawaited(this._ignoreSystemUiFailure(this._systemUiController.setControlsVisible(false)));
    });
  }

  _showControlsAndRestartTimer = (): void => {
    if (!this._controlsVisible) {
      this.setState(() => {
        this._controlsVisible = true;
      });
      unawaited(this._ignoreSystemUiFailure(this._systemUiController.setControlsVisible(true)));
    }
    this._scheduleControlsAutoHide();
  };

  async _ignoreSystemUiFailure(operation: Promise<void>): Promise<void> {
    try {
      await operation;
    } catch {
      // System UI is best-effort and must never block media interaction.
    }
  }

  override dispose(): void {
    this._controlsTimerGeneration++;
    this._controlsHideTimer?.cancel();
    this._removeResizeListener?.();
    unawaited(this._ignoreSystemUiFailure(this._systemUiController.restore()));
  }

  _handleKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>): void => {
    if (event.key === 'Escape' && !event.repeat) {
      this.widget.onExit();
    }
  };
}

export function LiveFullScreenStage(props: LiveFullScreenStageProps) {
  const state = useViewState(() => new _LiveFullScreenStageState(), props);
  const widget = props;
  const screenShareViewers = widget.screenShareViewers ?? [];
  // PORT: `MediaQuery.viewPaddingOf` has no DOM equivalent; desktop windows
  // have no system insets.
  const safeInsets = { left: 0, top: 0, right: 0, bottom: 0 };
  const visible = state._controlsVisible;
  const show = state._showControlsAndRestartTimer;
  return (
    <div
      ref={(node) => {
        state._focusNode.current = node;
      }}
      tabIndex={-1}
      data-testid="live-fullscreen-stage:pointer-region"
      onKeyDown={state._handleKeyDown}
      onPointerEnter={show}
      onPointerDown={show}
      onPointerMove={show}
      onPointerUp={show}
      onWheel={show}
      style={{
        position: 'relative',
        width: '100%',
        height: '100%',
        backgroundColor: UiColors.black,
        outline: 'none',
        overflow: 'hidden',
        cursor: visible ? undefined : SystemMouseCursors.none,
      }}
    >
      <div style={_fill}>
        <_LiveMediaVideo track={widget.track} fit={LiveVideoTrackFit.contain} mirrored={widget.cameraMirrored ?? false} />
      </div>
      <div style={{ position: 'absolute', left: 14 + safeInsets.left, top: 14 + safeInsets.top }}>
        <_FullScreenHoverReveal data-testid="live-fullscreen-stage:label-reveal" visible={visible}>
          <_LiveStageBadge
            label={widget.label}
            kind={widget.track.isScreenShare ? _LiveMediaKind.screenShare : _LiveMediaKind.camera}
          />
        </_FullScreenHoverReveal>
      </div>
      {widget.track.isScreenShare && screenShareViewers.length > 0 ? (
        <div style={{ position: 'absolute', left: 14 + safeInsets.left, bottom: 14 + safeInsets.bottom }}>
          <_FullScreenHoverReveal data-testid="live-fullscreen-stage:screen-viewers-reveal" visible={visible}>
            <_ScreenShareViewerPreview viewers={screenShareViewers} />
          </_FullScreenHoverReveal>
        </div>
      ) : null}
      <div style={{ position: 'absolute', top: 14 + safeInsets.top, right: 14 + safeInsets.right }}>
        <_FullScreenHoverReveal data-testid="live-fullscreen-stage:exit-reveal" visible={visible}>
          <_StageOverlayIconButton
            data-testid="live-fullscreen-stage:exit"
            icon={Icons.fullscreen_exit}
            infoMessage="退出全屏"
            onPressed={widget.onExit}
          />
        </_FullScreenHoverReveal>
      </div>
      {widget.track.isScreenShare && !widget.track.isLocal ? (
        <div style={{ position: 'absolute', right: 14 + safeInsets.right, bottom: 14 + safeInsets.bottom }}>
          <_FullScreenHoverReveal data-testid="live-fullscreen-stage:volume-reveal" visible={visible}>
            <_StageScreenShareVolumeButton
              value={widget.screenShareVolume}
              onChanged={widget.onScreenShareVolumeChanged}
              onPressed={widget.onScreenShareMuteToggled}
            />
          </_FullScreenHoverReveal>
        </div>
      ) : null}
    </div>
  );
}

function _StageOverlayIconButton({
  icon,
  infoMessage,
  onPressed,
  active = false,
  'data-testid': testId,
}: {
  icon: IconData;
  infoMessage: string;
  onPressed: (() => void) | null;
  active?: boolean;
  'data-testid'?: string;
}) {
  const [hovered, setHovered] = useState(false);
  const enabled = onPressed != null;
  return (
    <_HoverInfo message={infoMessage}>
      <div
        role="button"
        aria-disabled={!enabled}
        aria-pressed={active}
        aria-label={infoMessage}
        data-testid={testId}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        onClick={() => onPressed?.()}
        style={{ display: 'flex', cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic }}
      >
        <_StageOverlayIconSurface icon={icon} hovered={hovered} active={active} />
      </div>
    </_HoverInfo>
  );
}

function _StageOverlayIconSurface({
  icon,
  hovered: hoveredProp = null,
  active = false,
}: {
  icon: IconData;
  hovered?: boolean | null;
  active?: boolean;
}) {
  const [hoveredState, setHovered] = useState(false);
  const effectiveHovered = hoveredProp ?? hoveredState;
  const highlighted = effectiveHovered || active;
  const color = highlighted ? UiColors.accent : UiColors.textSecondary;
  const surface: CSSProperties = {
    width: 34,
    height: 34,
    flexShrink: 0,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: highlighted ? withAlpha(UiColors.surface, 0.92) : withAlpha(UiColors.surface, 0.72),
    borderRadius: UiRadii.md,
    transition: 'background-color 120ms linear',
  };
  if (hoveredProp != null) {
    return (
      <div style={surface}>
        <Icon icon={icon} size={19} color={color} />
      </div>
    );
  }
  return (
    <div
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{ ...surface, cursor: SystemMouseCursors.click }}
    >
      <Icon icon={icon} size={19} color={color} />
    </div>
  );
}

function _LiveStageBadge({ label, kind }: { label: string; kind: _LiveMediaKind }) {
  return (
    <div
      style={{
        boxSizing: 'border-box',
        backgroundColor: withAlpha(UiColors.surface, 0.86),
        borderRight: `1px solid ${UiColors.border}`,
        borderBottom: `1px solid ${UiColors.border}`,
        // The Dart border is painted over the 10/7 padding, not inside it.
        padding: '7px 9px 6px 10px',
      }}
    >
      <Row mainAxisSize="min" style={{ maxWidth: '100%' }}>
        <Icon icon={_mediaIcon(kind)} color={UiColors.accent} size={15} />
        <SizedBox width={6} />
        <Text maxLines={1} overflow="ellipsis" style={{ ...UiTypography.label, color: UiColors.text }}>
          {label}
        </Text>
      </Row>
    </div>
  );
}

function _ScreenShareViewerPreview({ viewers }: { viewers: readonly UserSummary[] }) {
  const avatarSize = 24.0;
  const overlap = 8.0;
  const config = useAppConfig();
  const visibleViewers = viewers.slice(0, 5);
  const avatarWidth = avatarSize + (visibleViewers.length - 1) * (avatarSize - overlap);
  return (
    <div
      data-testid="live-stage:screen-viewers"
      style={{
        boxSizing: 'border-box',
        backgroundColor: withAlpha(UiColors.surface, 0.9),
        border: `1px solid ${UiColors.border}`,
        borderRadius: UiRadii.md,
        // DecoratedBox paints its border over the 9/6 padding.
        padding: '5px 8px',
      }}
    >
      <Row mainAxisSize="min">
        <span data-testid="live-stage:screen-viewers-icon" style={{ display: 'flex' }}>
          <Icon icon={Icons.visibility} color={UiColors.accent} size={17} />
        </span>
        <SizedBox width={7} />
        <div style={{ position: 'relative', width: avatarWidth, height: avatarSize, flexShrink: 0 }}>
          {visibleViewers.map((viewer, index) => (
            <div key={`${viewer.id}:${index}`} style={{ position: 'absolute', left: index * (avatarSize - overlap), top: 0 }}>
              <Avatar
                label={room_display.userAvatarLabel(viewer)}
                imageUrl={config.resolveAssetUrl(viewer.avatarUrl)}
                defaultAvatarKey={viewer.defaultAvatarKey}
                size={avatarSize}
                active
                activeBorderWidth={1}
              />
            </div>
          ))}
        </div>
        <SizedBox width={7} />
        <Text softWrap={false} style={{ ...UiTypography.label, color: UiColors.white, fontWeight: 600, fontSize: 11 }}>
          {`共 ${viewers.length} 人`}
        </Text>
      </Row>
    </div>
  );
}

function _FullScreenHoverReveal({
  visible,
  children,
  'data-testid': testId,
}: {
  visible: boolean;
  children?: ReactNode;
  'data-testid'?: string;
}) {
  return (
    <div
      data-testid={testId}
      style={{
        pointerEvents: visible ? undefined : 'none',
        opacity: visible ? 1 : 0,
        transition: `opacity ${visible ? 0 : 160}ms linear`,
      }}
    >
      {children}
    </div>
  );
}

function _mediaIcon(kind: _LiveMediaKind): IconData {
  switch (kind) {
    case _LiveMediaKind.camera:
      return Icons.videocam;
    case _LiveMediaKind.screenShare:
      return Icons.screen_share;
  }
}

function _screenShareVolumeIcon(value: number): IconData {
  return _volumeLevelIcon(normalizedAudioVolume(value));
}

export function liveStageTrackLabel(live: LiveState | null | undefined, track: LiveVideoTrack): string {
  const name = live_display.liveParticipantDisplayName(live, track.identity);
  if (track.isScreenShare) return live_display.liveScreenShareStageLabel(name);
  return name.length === 0 ? '摄像头' : `${name} 的摄像头`;
}

function _trackForSelection(tracks: readonly LiveVideoTrack[], selection: LiveStageSelection): LiveVideoTrack | null {
  if (selection.mode !== LiveStageSelectionMode.track) return null;
  for (const track of tracks) {
    if (track.identity === selection.identity && track.isScreenShare === selection.isScreenShare) {
      return track;
    }
  }
  return null;
}

/** Dart `@visibleForTesting resolveLiveStageTrackForTest`. */
export function resolveLiveStageTrackForTest(opts: {
  tracks: readonly LiveVideoTrack[];
  selection: LiveStageSelection | null;
}): LiveVideoTrack | null {
  return _resolveLiveStageTrack(opts);
}

export function _resolveLiveStageTrack(opts: {
  tracks: readonly LiveVideoTrack[];
  selection: LiveStageSelection | null;
}): LiveVideoTrack | null {
  const { tracks, selection } = opts;
  if (selection != null) {
    if (selection.mode === LiveStageSelectionMode.none) return null;
    const track = _trackForSelection(tracks, selection);
    if (track != null) return track;
    return null;
  }

  return _localScreenShare(tracks);
}

function _localScreenShare(tracks: readonly LiveVideoTrack[]): LiveVideoTrack | null {
  for (const track of tracks) {
    if (track.isLocal && track.isScreenShare) return track;
  }
  return null;
}

export function _memberPreviewTrack(opts: {
  tracks: readonly LiveVideoTrack[];
  userId: string;
  stageTrack: LiveVideoTrack | null;
}): LiveVideoTrack | null {
  const { tracks, userId, stageTrack } = opts;
  const camera = _cameraFor(tracks, userId);
  const share = _screenShareFor(tracks, userId);
  if (stageTrack?.identity === userId) {
    if (stageTrack.isScreenShare) return camera;
    return share;
  }
  return camera ?? share;
}

export function _selectableTrack(opts: {
  tracks: readonly LiveVideoTrack[];
  userId: string;
  stageTrack: LiveVideoTrack | null;
}): LiveVideoTrack | null {
  const preview = _memberPreviewTrack(opts);
  if (preview != null) return preview;

  return null;
}

function _screenShareFor(tracks: readonly LiveVideoTrack[], userId: string): LiveVideoTrack | null {
  for (const track of tracks) {
    if (track.isScreenShare && track.identity === userId) return track;
  }
  return null;
}

function _cameraFor(tracks: readonly LiveVideoTrack[], userId: string): LiveVideoTrack | null {
  for (const track of tracks) {
    if (!track.isScreenShare && track.identity === userId) return track;
  }
  return null;
}
