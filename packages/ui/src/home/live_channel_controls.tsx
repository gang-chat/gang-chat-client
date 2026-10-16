/**
 * Port of `lib/src/home/live_channel_controls.dart` (part of `live_channel_pane`).
 */
import { useRef, type CSSProperties, type PointerEvent as ReactPointerEvent, type ReactNode, type RefObject } from 'react';

import * as live_display from '@gang-chat/core/app/live_display';
import { audioVolumePercentText } from '@gang-chat/core/app/audio_levels';
import * as music_box_display from '@gang-chat/core/app/music_box_display';
import type { MusicBoxController } from '@gang-chat/core/app/music_box_controller';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { Timer } from '@gang-chat/core/lib/async';
import type { CurrentUser, MusicBoxQueueItem, MusicBoxState, PublicRoom } from '@gang-chat/core/protocol/models';

import { Center, Column, Row, SizedBox, Text } from '../lib/layout';
import { kLongPressTimeout, SystemMouseCursors } from '../lib/mouse_cursors';
import { useElementSize } from '../lib/use_element_size';
import { useViewState, ViewState } from '../lib/view_state';
import { Button, ButtonIcon, ButtonTone } from '../ui/button';
import { Icons, type IconData } from '../ui/icon';
import { insertOverlay, type OverlayEntryHandle } from '../ui/overlay';
import { OverflowMarqueeText } from '../ui/overflow_marquee_text';
import { UiTooltip } from '../ui/tooltip';
import { UiColors, UiDefaultTextStyle, UiRadii, UiTypography, argb, withAlpha } from '../ui/tokens';
import { HoverCardAnchor } from './hover_card_anchor';
import { _VinylRecord } from './live_channel_music_box';
import { _MusicBoxSongCard } from './live_channel_music_box_song_card';
import { _controlButtonSize, _controlHoverInfoVerticalOffset } from './live_channel_pane';
import type { RoomProfileResolver, UserProfileActionBuilder, UserProfileResolver } from './room_profile_card';

function _clampDouble(value: number, lower: number, upper: number): number {
  return Math.min(Math.max(value, lower), upper);
}

export interface _LiveControlBarProps {
  joined: boolean;
  joining: boolean;
  micMuted: boolean;
  headphonesMuted: boolean;
  voiceBlocked: boolean;
  cameraOn: boolean;
  screenSharing: boolean;
  inputVolume: number;
  outputVolume: number;
  musicBox: MusicBoxState | null;
  musicBoxEnabled: boolean;
  musicBoxOpen: boolean;
  onJoin: (() => void) | null;
  onLeave: () => void;
  onToggleMic: (() => void) | null;
  onToggleHeadphones: () => void;
  onToggleCamera: () => void;
  onToggleShare: (() => void) | null;
  onInputVolumeChanged: (volume: number) => void;
  onOutputVolumeChanged: (volume: number) => void;
  onToggleMusicBox: () => void;
  onMusicBoxTogglePlayback: () => void;
  onMusicBoxSkip: () => void;
  musicBoxController: MusicBoxController | null;
  musicBoxRoomId: string | null;
  onMusicBoxStateChanged: ((state: MusicBoxState) => void) | null;
  currentUser: CurrentUser;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder: UserProfileActionBuilder | null;
  onCollapse: () => void;
}

export function _LiveControlBar(props: _LiveControlBarProps) {
  const {
    joined,
    joining,
    micMuted,
    headphonesMuted,
    voiceBlocked,
    cameraOn,
    screenSharing,
    inputVolume,
    outputVolume,
    musicBox,
    musicBoxEnabled,
    musicBoxOpen,
    onJoin,
    onLeave,
    onToggleMic,
    onToggleHeadphones,
    onToggleCamera,
    onToggleShare,
    onInputVolumeChanged,
    onOutputVolumeChanged,
    onCollapse,
  } = props;

  // LayoutBuilder: the bar is laid out at the body's full width with a
  // natural height. Unmeasured counts as wide (non-compact).
  const layoutRef = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(layoutRef);
  const maxWidth = size?.width ?? Infinity;

  const compact = maxWidth < 430;
  const micControl = live_display.liveMicControlState({ micMuted, voiceBlocked });
  const controls: ReactNode[] = [
    <_HoverVolumeButton
      key="live-control:mic"
      data-testid="live-control:mic"
      value={inputVolume}
      semanticLabel="麦克风输入音量"
      infoMessage={micControl.mutedForDisplay ? '打开麦克风' : '关闭麦克风'}
      onChanged={onInputVolumeChanged}
    >
      <ButtonIcon
        icon={micControl.mutedForDisplay ? Icons.mic_off : Icons.mic}
        selected={micControl.active}
        onPressed={micControl.enabled ? onToggleMic : null}
        size={_controlButtonSize}
      />
    </_HoverVolumeButton>,
    <_HoverVolumeButton
      key="live-control:headphones"
      data-testid="live-control:headphones"
      value={outputVolume}
      semanticLabel="语音输出音量"
      infoMessage={headphonesMuted ? '打开耳机' : '关闭耳机'}
      onChanged={onOutputVolumeChanged}
    >
      <ButtonIcon
        icon={headphonesMuted ? Icons.headset_off : Icons.headphones}
        selected={!headphonesMuted}
        onPressed={onToggleHeadphones}
        size={_controlButtonSize}
      />
    </_HoverVolumeButton>,
    <_HoverInfo key="live-control:camera" message={cameraOn ? '关闭摄像头' : '开启摄像头'}>
      <ButtonIcon
        data-testid="live-control:camera"
        icon={cameraOn ? Icons.videocam : Icons.videocam_outlined}
        selected={cameraOn}
        onPressed={onToggleCamera}
        size={_controlButtonSize}
      />
    </_HoverInfo>,
    ...(onToggleShare != null
      ? [
          <_HoverInfo key="live-control:screen-share" message={screenSharing ? '停止共享屏幕' : '共享屏幕'}>
            <ButtonIcon
              data-testid="live-control:screen-share"
              icon={screenSharing ? Icons.stop_screen_share : Icons.screen_share_outlined}
              selected={screenSharing}
              onPressed={onToggleShare}
              size={_controlButtonSize}
            />
          </_HoverInfo>,
        ]
      : []),
    <_HoverInfo key="live-control:leave" message={joining ? '正在加入语音' : '离开语音频道'}>
      <ButtonIcon
        data-testid="live-control:leave"
        icon={Icons.call_end}
        tone={ButtonTone.danger}
        onPressed={joining ? null : onLeave}
        size={_controlButtonSize}
      />
    </_HoverInfo>,
  ];

  const musicBoxStrip =
    joined && musicBoxEnabled && musicBox != null ? (
      <_InlineMusicBox
        state={musicBox}
        expanded={musicBoxOpen}
        onTogglePlayback={props.onMusicBoxTogglePlayback}
        onSkip={props.onMusicBoxSkip}
        onToggleExpand={props.onToggleMusicBox}
        controller={props.musicBoxController}
        roomId={props.musicBoxRoomId}
        onStateChanged={props.onMusicBoxStateChanged}
        currentUser={props.currentUser}
        onResolveUserProfile={props.onResolveUserProfile}
        onResolveRoomProfile={props.onResolveRoomProfile}
        onEnterCommonRoom={props.onEnterCommonRoom}
        userProfileActionBuilder={props.userProfileActionBuilder}
      />
    ) : null;

  const compactCollapseButton = (opts: { showLabel: boolean; key?: string }): ReactNode => (
    <_HoverInfo key={opts.key} message="收起语音频道">
      {opts.showLabel ? (
        <Button
          data-testid="live-control:collapse"
          width={Infinity}
          height={_controlButtonSize}
          icon={Icons.keyboard_arrow_up}
          onPressed={onCollapse}
        >
          收起
        </Button>
      ) : (
        <ButtonIcon
          data-testid="live-control:collapse"
          icon={Icons.keyboard_arrow_up}
          onPressed={onCollapse}
          size={_controlButtonSize}
        />
      )}
    </_HoverInfo>
  );

  let content: ReactNode;
  if (compact) {
    const joinedControlsFitOneRow =
      (controls.length + 1) * _controlButtonSize + controls.length * 10 <= maxWidth;
    const controlSection = !joined ? (
      <Column crossAxisAlignment="stretch">
        <_HoverInfo message={joining ? '正在加入语音' : '加入语音频道'}>
          <Button
            data-testid="live-control:join"
            width={Infinity}
            height={_controlButtonSize}
            loading={joining}
            icon={Icons.call}
            onPressed={onJoin}
          >
            加入
          </Button>
        </_HoverInfo>
        <SizedBox height={10} />
        {compactCollapseButton({ showLabel: true })}
      </Column>
    ) : joinedControlsFitOneRow ? (
      <Center>
        <Row mainAxisSize="min">
          {_withControlGaps([...controls, compactCollapseButton({ showLabel: false, key: 'live-control:collapse' })])}
        </Row>
      </Center>
    ) : (
      <Column crossAxisAlignment="stretch">
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            flexWrap: 'wrap',
            justifyContent: 'center',
            alignItems: 'flex-start',
            gap: 10,
          }}
        >
          {controls}
        </div>
        <SizedBox height={10} />
        {compactCollapseButton({ showLabel: true })}
      </Column>
    );
    content = (
      <Column crossAxisAlignment="stretch">
        {musicBoxStrip != null ? (
          <>
            <Center>
              <div style={{ width: '100%', maxWidth: 280, minWidth: 0 }}>{musicBoxStrip}</div>
            </Center>
            <SizedBox height={10} />
          </>
        ) : null}
        {controlSection}
      </Column>
    );
  } else {
    // The inline music box sits on its own centered row above the
    // transport buttons, so it never competes with them for horizontal
    // space — it just ellipsizes its title/artist within the 280 cap.
    const buttonRow = (
      <Row mainAxisSize="min">
        {!joined ? (
          <>
            <_HoverInfo message={joining ? '正在加入语音' : '加入语音频道'}>
              <Button
                data-testid="live-control:join"
                height={_controlButtonSize}
                loading={joining}
                icon={Icons.call}
                onPressed={onJoin}
              >
                加入
              </Button>
            </_HoverInfo>
            <SizedBox width={12} />
          </>
        ) : (
          <>
            {_withControlGaps(controls)}
            <SizedBox width={10} />
          </>
        )}
        <_HoverInfo message="收起语音频道">
          {!joined ? (
            <Button
              data-testid="live-control:collapse"
              height={_controlButtonSize}
              icon={Icons.keyboard_arrow_up}
              onPressed={onCollapse}
            >
              收起
            </Button>
          ) : (
            <ButtonIcon
              data-testid="live-control:collapse"
              icon={Icons.keyboard_arrow_up}
              onPressed={onCollapse}
              size={_controlButtonSize}
            />
          )}
        </_HoverInfo>
      </Row>
    );

    if (musicBoxStrip == null) {
      content = <Center>{buttonRow}</Center>;
    } else {
      content = (
        <Column mainAxisSize="min" crossAxisAlignment="stretch">
          <Center>
            <div style={{ width: '100%', maxWidth: 280, minWidth: 0 }}>{musicBoxStrip}</div>
          </Center>
          <SizedBox height={10} />
          <Center>{buttonRow}</Center>
        </Column>
      );
    }
  }

  return (
    <div ref={layoutRef} style={{ width: '100%', minWidth: 0 }}>
      {content}
    </div>
  );
}

function _withControlGaps(children: readonly ReactNode[]): ReactNode[] {
  const result: ReactNode[] = [];
  for (let index = 0; index < children.length; index++) {
    if (index > 0) result.push(<SizedBox key={`gap-${index}`} width={10} />);
    result.push(children[index]);
  }
  return result;
}

export const _hoverVolumePanelHeight = 144.0;
const _hoverVolumeTrackThickness = 7.0;
const _hoverVolumeThumbWidth = 26.0;
const _hoverVolumeThumbHeight = 7.0;
const _hoverVolumePercentWidth = 40.0;
const _hoverVolumePercentHeight = 18.0;
const _hoverVolumePercentGap = 6.0;

export function _volumeLevelIcon(volume: number): IconData {
  if (volume <= 0) return Icons.volume_off;
  if (volume < 0.5) return Icons.volume_down;
  return Icons.volume_up;
}

export function _HoverInfo({ message, children }: { message: string; children?: ReactNode }) {
  if (message.length === 0) return <>{children}</>;
  return (
    <UiTooltip
      message={message}
      preferBelow
      verticalOffset={_controlHoverInfoVerticalOffset}
      triggerMode={HostPlatform.isAndroid ? 'manual' : null}
    >
      {children}
    </UiTooltip>
  );
}

export interface _HoverVolumeButtonProps {
  children?: ReactNode;
  value: number;
  semanticLabel: string;
  infoMessage: string;
  onChanged: (value: number) => void;
  maxValue?: number;
  valueFormatter?: ((value: number) => string) | null;
  panelWidth?: number;
  panelHeight?: number;
  /**
   * DOM addition: style of the hover target box. It shrink-wraps [children]
   * by default; Flutter callers that handed it tight constraints (the member
   * status row) make it fill instead.
   */
  style?: CSSProperties;
  /** Dart `key` of the button (test id). */
  'data-testid'?: string;
}

export class _HoverVolumeButtonState extends ViewState<_HoverVolumeButtonProps> {
  static _active: _HoverVolumeButtonState | null = null;

  _overlayEntry: OverlayEntryHandle | null = null;
  _hideTimer: Timer | null = null;
  _longPressTimer: Timer | null = null;
  _targetHovered = false;
  _overlayHovered = false;
  _dragging = false;
  private _valueSlot: number | null = null;
  readonly _targetKey: RefObject<HTMLDivElement | null> = { current: null };

  /** Dart `late double _value = _normalize(widget.value)`. */
  get _value(): number {
    this._valueSlot ??= this._normalize(this.widget.value);
    return this._valueSlot;
  }

  set _value(value: number) {
    this._valueSlot = value;
  }

  get _maxValue(): number {
    const maxValue = this.widget.maxValue ?? 1.0;
    return maxValue <= 0 ? 1.0 : maxValue;
  }

  get _panelWidth(): number {
    return Math.max(this.widget.panelWidth ?? _controlButtonSize, 1.0);
  }

  get _panelHeight(): number {
    return Math.max(this.widget.panelHeight ?? _hoverVolumePanelHeight, 1.0);
  }

  _normalize(value: number): number {
    return _clampDouble(value, 0.0, this._maxValue);
  }

  override didUpdateProps(oldWidget: _HoverVolumeButtonProps): void {
    if (oldWidget.value !== this.widget.value || oldWidget.maxValue !== this.widget.maxValue) {
      this._value = this._normalize(this.widget.value);
      this._markOverlayNeedsBuild();
    }
  }

  override dispose(): void {
    this._hideTimer?.cancel();
    this._longPressTimer?.cancel();
    this._hideOverlay();
  }

  _showOverlay = (): void => {
    this._hideTimer?.cancel();
    const active = _HoverVolumeButtonState._active;
    if (active != null && active !== this) {
      active._hideOverlay();
    }
    _HoverVolumeButtonState._active = this;
    if (this._overlayEntry != null) {
      this._overlayEntry.markNeedsBuild();
      return;
    }
    const targetBox = this._targetKey.current;
    if (targetBox == null || !targetBox.isConnected) return;
    // Window coordinates of the target, including any ancestor scaling (the
    // member cards are drawn through a FittedBox-like transform).
    const targetRect = targetBox.getBoundingClientRect();
    const targetSize = { width: targetBox.offsetWidth, height: targetBox.offsetHeight };
    const overlaySize = { width: window.innerWidth, height: window.innerHeight };
    const horizontalScale = targetSize.width <= 0 ? 1.0 : targetRect.width / targetSize.width;
    const verticalScale = targetSize.height <= 0 ? 1.0 : targetRect.height / targetSize.height;
    const panelWidth = this._panelWidth * horizontalScale;
    const panelHeight = this._panelHeight * verticalScale;
    const maxPanelLeft = Math.max(overlaySize.width - panelWidth, 0.0);
    const maxPanelTop = Math.max(overlaySize.height - panelHeight, 0.0);
    const panelLeft = _clampDouble(targetRect.left + (targetRect.width - panelWidth) / 2, 0.0, maxPanelLeft);
    const panelTop = _clampDouble(targetRect.top - panelHeight - 8 * verticalScale, 0.0, maxPanelTop);
    this._overlayEntry = insertOverlay(() =>
      this._buildOverlay({
        left: panelLeft,
        top: panelTop,
        width: panelWidth,
        height: panelHeight,
      }),
    );
  };

  _hideOverlay(): void {
    this._hideTimer?.cancel();
    this._hideTimer = null;
    if (_HoverVolumeButtonState._active === this) _HoverVolumeButtonState._active = null;
    this._overlayEntry?.remove();
    this._overlayEntry = null;
  }

  _scheduleHide(opts: { delay?: number } = {}): void {
    const { delay = 90 } = opts;
    this._hideTimer?.cancel();
    this._hideTimer = new Timer(delay, () => {
      if (!this._targetHovered && !this._overlayHovered && !this._dragging) {
        this._hideOverlay();
      }
    });
  }

  _markOverlayNeedsBuild(): void {
    if (this._overlayEntry == null) return;
    // didUpdateProps already runs after the frame (Dart used a post-frame
    // callback to avoid rebuilding the overlay during build).
    if (this.mounted) this._overlayEntry?.markNeedsBuild();
  }

  _setValue = (value: number): void => {
    const normalized = this._normalize(value);
    this.setState(() => {
      this._value = normalized;
    });
    this._overlayEntry?.markNeedsBuild();
    this.widget.onChanged(normalized);
  };

  _showOverlayForAndroidLongPress(): void {
    this._showOverlay();
    this._scheduleHide({ delay: 3000 });
  }

  _buildOverlay(opts: { left: number; top: number; width: number; height: number }): ReactNode {
    const { left, top, width, height } = opts;
    return (
      <div
        style={{ position: 'absolute', left, top, width, height, pointerEvents: 'auto' }}
        onPointerEnter={(event) => {
          if (event.pointerType === 'touch') return;
          this._overlayHovered = true;
          this._showOverlay();
        }}
        onPointerLeave={(event) => {
          if (event.pointerType === 'touch') return;
          this._overlayHovered = false;
          this._scheduleHide();
        }}
      >
        <_HoverVolumePanel
          value={this._value}
          maxValue={this._maxValue}
          valueFormatter={this.widget.valueFormatter ?? null}
          width={width}
          height={height}
          semanticLabel={this.widget.semanticLabel}
          onChanged={this._setValue}
          onChangeStart={() => {
            this._dragging = true;
            this._showOverlay();
          }}
          onChangeEnd={() => {
            this._dragging = false;
            this._scheduleHide();
          }}
        />
      </div>
    );
  }

  // PORT: Flutter's `onLongPressStart` (Android only) as a pointer timer.
  _handleTargetPointerDown = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (!HostPlatform.isAndroid || event.pointerType === 'mouse') return;
    this._longPressTimer?.cancel();
    this._longPressTimer = new Timer(kLongPressTimeout, () => {
      this._longPressTimer = null;
      if (this.mounted) this._showOverlayForAndroidLongPress();
    });
  };

  _cancelLongPress = (): void => {
    this._longPressTimer?.cancel();
    this._longPressTimer = null;
  };
}

export function _HoverVolumeButton(props: _HoverVolumeButtonProps) {
  const state = useViewState(() => new _HoverVolumeButtonState(), props);
  return (
    <_HoverInfo message={props.infoMessage}>
      <div
        ref={(node) => {
          state._targetKey.current = node;
        }}
        data-testid={props['data-testid']}
        style={{ display: 'flex', width: 'fit-content', flexShrink: 0, ...props.style }}
        onPointerDown={state._handleTargetPointerDown}
        onPointerUp={state._cancelLongPress}
        onPointerCancel={state._cancelLongPress}
        onPointerEnter={(event) => {
          if (event.pointerType === 'touch') return;
          state._targetHovered = true;
          state._showOverlay();
        }}
        onPointerLeave={(event) => {
          if (event.pointerType === 'touch') return;
          state._targetHovered = false;
          state._scheduleHide();
        }}
      >
        {props.children}
      </div>
    </_HoverInfo>
  );
}

interface _HoverVolumePanelProps {
  value: number;
  maxValue: number;
  width: number;
  height: number;
  semanticLabel: string;
  onChanged: (value: number) => void;
  onChangeStart: (value: number) => void;
  onChangeEnd: (value: number) => void;
  valueFormatter?: ((value: number) => string) | null;
}

function _HoverVolumePanel(props: _HoverVolumePanelProps) {
  const { value, maxValue, width, height, semanticLabel, onChanged, onChangeStart, onChangeEnd, valueFormatter } = props;
  const normalized = _clampDouble(value, 0.0, maxValue);
  const scale = _clampDouble(width / _controlButtonSize, 0.65, 1.0);
  const volumePercentText = valueFormatter?.(normalized) ?? audioVolumePercentText(normalized);
  return (
    <div
      role="slider"
      aria-label={semanticLabel}
      aria-valuetext={volumePercentText}
      data-testid={`live-volume-panel:${semanticLabel}`}
      style={{
        width,
        height,
        boxSizing: 'border-box',
        backgroundColor: UiColors.surface,
        borderRadius: UiRadii.md,
        // Flutter's DecoratedBox paints its border without insetting the
        // child; an inset shadow keeps the 12px padding from the outer edge.
        boxShadow: `inset 0 0 0 1px ${UiColors.border}, 0px 8px 18px 0px ${argb(0x66080a0d)}`,
        paddingTop: 12 * scale,
        paddingBottom: 12 * scale,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <_HoverVolumeSlider
        value={normalized}
        maxValue={maxValue}
        scale={scale}
        valueFormatter={valueFormatter}
        semanticLabel={semanticLabel}
        onChanged={onChanged}
        onChangeStart={onChangeStart}
        onChangeEnd={onChangeEnd}
      />
    </div>
  );
}

interface _HoverVolumeSliderProps {
  value: number;
  maxValue: number;
  scale: number;
  semanticLabel: string;
  onChanged: (value: number) => void;
  onChangeStart: (value: number) => void;
  onChangeEnd: (value: number) => void;
  valueFormatter?: ((value: number) => string) | null;
}

class _HoverVolumeSliderState extends ViewState<_HoverVolumeSliderProps> {
  _pointer: number | null = null;
  _thumbHovered = false;
  _dragging = false;
  private _interactionSlot: number | null = null;
  readonly _boxKey: RefObject<HTMLDivElement | null> = { current: null };

  /** Dart `late double _interactionValue = _value`. */
  get _interactionValue(): number {
    this._interactionSlot ??= this._value;
    return this._interactionSlot;
  }

  set _interactionValue(value: number) {
    this._interactionSlot = value;
  }

  get _maxValue(): number {
    return this.widget.maxValue <= 0 ? 1.0 : this.widget.maxValue;
  }
  get _scale(): number {
    return _clampDouble(this.widget.scale, 0.65, 1.0);
  }
  get _thumbWidth(): number {
    return _hoverVolumeThumbWidth * this._scale;
  }
  get _thumbHeight(): number {
    return _hoverVolumeThumbHeight * this._scale;
  }
  get _trackThickness(): number {
    return _hoverVolumeTrackThickness * this._scale;
  }
  get _percentGap(): number {
    return _hoverVolumePercentGap * this._scale;
  }
  get _percentWidth(): number {
    return _hoverVolumePercentWidth * this._scale;
  }
  get _percentHeight(): number {
    return _hoverVolumePercentHeight * this._scale;
  }

  get _value(): number {
    return _clampDouble(this.widget.value, 0.0, this._maxValue);
  }
  get _fraction(): number {
    return _clampDouble(this._value / this._maxValue, 0.0, 1.0);
  }

  _fractionFromPosition(localY: number, height: number): number {
    const travel = height - this._thumbHeight;
    if (travel <= 0) return 0;
    return _clampDouble(1 - (localY - this._thumbHeight / 2) / travel, 0.0, 1.0);
  }

  _emitFromPosition(clientY: number): void {
    const box = this._boxKey.current;
    if (box == null) return;
    const rect = box.getBoundingClientRect();
    const fraction = this._fractionFromPosition(clientY - rect.top, box.offsetHeight);
    const value = fraction * this._maxValue;
    this._interactionValue = value;
    this.widget.onChanged(value);
  }

  _handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>): void => {
    // PORT: only the primary button drags (the DOM routes secondary clicks to
    // context menus); see PressableSurface.
    if (event.button !== 0) return;
    if (this._pointer != null) return;
    this._pointer = event.pointerId;
    // Flutter keeps delivering a pointer's events to the widgets it first
    // hit; pointer capture does the same once the pointer leaves the slider.
    try {
      event.currentTarget.setPointerCapture(event.pointerId);
    } catch {
      // Capture is best-effort (synthetic events in tests have no pointer).
    }
    this._interactionValue = this._value;
    this.setState(() => {
      this._dragging = true;
    });
    this.widget.onChangeStart(this._interactionValue);
    this._emitFromPosition(event.clientY);
  };

  _handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (event.pointerId !== this._pointer) return;
    this._emitFromPosition(event.clientY);
  };

  _handlePointerUp = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (event.pointerId !== this._pointer) return;
    this._pointer = null;
    this.setState(() => {
      this._dragging = false;
    });
    this.widget.onChangeEnd(this._interactionValue);
  };

  _handlePointerCancel = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (event.pointerId !== this._pointer) return;
    this._pointer = null;
    this.setState(() => {
      this._dragging = false;
    });
    this.widget.onChangeEnd(this._interactionValue);
  };

  override didUpdateProps(): void {
    if (!this._dragging) this._interactionValue = this._value;
  }

  _sidePercentBottom(opts: { height: number; thumbBottom: number; thumbHeight: number; percentHeight: number }): number {
    const { height, thumbBottom, thumbHeight, percentHeight } = opts;
    return _clampDouble(thumbBottom + (thumbHeight - percentHeight) / 2, 0.0, height - percentHeight);
  }
}

function _HoverVolumeSlider(props: _HoverVolumeSliderProps) {
  const state = useViewState(() => new _HoverVolumeSliderState(), props);
  // LayoutBuilder: the slider takes the panel's full inner height.
  const size = useElementSize(state._boxKey);
  const percentText = props.valueFormatter?.(state._value) ?? audioVolumePercentText(state._value);
  const showPercent = state._thumbHovered || state._dragging;

  const height = size?.height ?? 0;
  const thumbHeight = state._thumbHeight;
  const thumbWidth = state._thumbWidth;
  const trackThickness = state._trackThickness;
  const percentGap = state._percentGap;
  const percentWidth = state._percentWidth;
  const percentHeight = state._percentHeight;
  const travel = Math.max(height - thumbHeight, 0.0);
  const thumbBottom = travel * state._fraction;
  const labelBottom = state._sidePercentBottom({ height, thumbBottom, thumbHeight, percentHeight });

  return (
    <div
      ref={(node) => {
        state._boxKey.current = node;
      }}
      data-testid={`live-volume-slider:${props.semanticLabel}`}
      onPointerDown={state._handlePointerDown}
      onPointerMove={state._handlePointerMove}
      onPointerUp={state._handlePointerUp}
      onPointerCancel={state._handlePointerCancel}
      style={{
        position: 'relative',
        width: thumbWidth,
        height: '100%',
        flexShrink: 0,
        cursor: SystemMouseCursors.click,
        touchAction: 'none',
      }}
    >
      {size != null ? (
        <>
          <_HoverVolumeTrack thumbWidth={thumbWidth} trackThickness={trackThickness} />
          <div
            data-testid={`live-volume-fill:${props.semanticLabel}`}
            style={{
              position: 'absolute',
              left: (thumbWidth - trackThickness) / 2,
              bottom: 0,
              width: trackThickness,
              height: thumbBottom,
              backgroundColor: UiColors.accent,
              borderBottomLeftRadius: trackThickness / 2,
              borderBottomRightRadius: trackThickness / 2,
            }}
          />
          {showPercent ? (
            <div
              data-testid={`live-volume-percent:${props.semanticLabel}`}
              style={{
                position: 'absolute',
                left: thumbWidth + percentGap,
                bottom: labelBottom,
                width: percentWidth,
                height: percentHeight,
                backgroundColor: UiColors.white,
                borderRadius: UiRadii.sm,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                pointerEvents: 'none',
              }}
            >
              <Text
                softWrap={false}
                style={{ ...UiTypography.label, color: withAlpha(UiColors.black, 0.87), fontSize: 10 * state._scale }}
              >
                {percentText}
              </Text>
            </div>
          ) : null}
          <div
            data-testid={`live-volume-thumb:${props.semanticLabel}`}
            onPointerEnter={() =>
              state.setState(() => {
                state._thumbHovered = true;
              })
            }
            onPointerLeave={() =>
              state.setState(() => {
                state._thumbHovered = false;
              })
            }
            style={{
              position: 'absolute',
              left: 0,
              bottom: thumbBottom,
              width: thumbWidth,
              height: thumbHeight,
              backgroundColor: UiColors.text,
              borderRadius: thumbHeight / 2,
            }}
          />
        </>
      ) : null}
    </div>
  );
}

function _HoverVolumeTrack({ thumbWidth, trackThickness }: { thumbWidth: number; trackThickness: number }) {
  return (
    <div
      style={{
        position: 'absolute',
        left: (thumbWidth - trackThickness) / 2,
        top: 0,
        bottom: 0,
        width: trackThickness,
        backgroundColor: UiColors.surfacePressed,
        borderRadius: trackThickness / 2,
      }}
    />
  );
}

interface _InlineMusicBoxProps {
  state: MusicBoxState;
  expanded: boolean;
  onTogglePlayback: () => void;
  onSkip: () => void;
  onToggleExpand: () => void;
  controller: MusicBoxController | null;
  roomId: string | null;
  onStateChanged: ((state: MusicBoxState) => void) | null;
  currentUser: CurrentUser;
  onResolveUserProfile: UserProfileResolver | null;
  onResolveRoomProfile: RoomProfileResolver | null;
  onEnterCommonRoom: ((room: PublicRoom) => void) | null;
  userProfileActionBuilder: UserProfileActionBuilder | null;
}

/**
 * The now-playing strip embedded inline at the right of the live control bar:
 * a small spinning vinyl, the current track, transport controls, and a button
 * that expands the full search + queue panel. Replaces the old standalone
 * music box button so the player no longer claims its own content column.
 */
function _InlineMusicBox(props: _InlineMusicBoxProps) {
  const { state, expanded, onTogglePlayback, onSkip, onToggleExpand } = props;
  const current = state.currentItem;
  const spinning = music_box_display.musicBoxRecordSpinning(state);
  const transport = music_box_display.musicBoxPrimaryTransport(state);
  const hasQueue = state.queue.length > 0;
  const isPause = transport === music_box_display.MusicBoxTransportAction.pause;

  // LayoutBuilder over the strip's width.
  const stripRef = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(stripRef);
  // Drop the skip button once the strip narrows so the title/artist
  // can keep shrinking instead of the whole strip bottoming out at
  // the full button-row width.
  const showSkip = (size?.width ?? Infinity) >= 175;

  return (
    <div
      ref={stripRef}
      data-testid="live-control:music-inline"
      style={{ position: 'relative', width: '100%', maxWidth: 280, height: _controlButtonSize, minWidth: 0 }}
    >
      {/* Flutter's Stack places the row at its top-start with its natural
          height; only the progress track is pinned to the bottom edge. */}
      <Row>
        <_VinylRecord spinning={spinning} label={current?.title ?? null} size={26} />
        <SizedBox width={8} />
        <div style={{ flex: '1 1 0', minWidth: 0 }}>
          {current == null ? (
            <_InlineMusicBoxIdentity current={current} />
          ) : (
            <HoverCardAnchor
              resetKey={JSON.stringify([
                current.id,
                current.status,
                current.requestedBy?.avatarUrl ?? null,
                current.requestedBy?.avatarLabel ?? null,
                state.activeSource.type,
                state.activeSource.id,
                state.activeSource.name,
              ])}
              cardWidth={310}
              cardBuilder={() =>
                _MusicBoxSongCard.queue({
                  item: current,
                  isCurrent: true,
                  queueSongCount: state.queue.length,
                  controller: props.controller,
                  roomId: props.roomId,
                  activeSource: state.activeSource,
                  onStateChanged: props.onStateChanged,
                  currentUser: props.currentUser,
                  onResolveUserProfile: props.onResolveUserProfile,
                  onResolveRoomProfile: props.onResolveRoomProfile,
                  onEnterCommonRoom: props.onEnterCommonRoom,
                  userProfileActionBuilder: props.userProfileActionBuilder,
                })
              }
            >
              <_InlineMusicBoxIdentity current={current} />
            </HoverCardAnchor>
          )}
        </div>
        <SizedBox width={6} />
        <_HoverInfo message={isPause ? '暂停音乐' : '播放音乐'}>
          <ButtonIcon
            data-testid="live-control:music-toggle"
            icon={isPause ? Icons.pause : Icons.play_arrow}
            tone={ButtonTone.primary}
            onPressed={hasQueue ? onTogglePlayback : null}
            size={30}
          />
        </_HoverInfo>
        {showSkip ? (
          <>
            <SizedBox width={4} />
            <_HoverInfo message="下一首">
              <ButtonIcon
                data-testid="live-control:music-skip"
                icon={Icons.skip_next}
                onPressed={hasQueue ? onSkip : null}
                size={30}
              />
            </_HoverInfo>
          </>
        ) : null}
        <SizedBox width={4} />
        <_HoverInfo message={expanded ? '收起音乐面板' : '打开音乐面板'}>
          <ButtonIcon
            data-testid="live-control:music-queue"
            icon={Icons.library_music}
            selected={expanded}
            onPressed={onToggleExpand}
            size={30}
          />
        </_HoverInfo>
      </Row>
      {/* A hairline progress track pinned to the bottom edge of the strip,
          spanning its full width — keeps the title/artist lines intact
          while still reading playback position at a glance. */}
      <div style={{ position: 'absolute', left: 0, right: 0, bottom: 0 }}>
        <_InlineProgress state={state} />
      </div>
    </div>
  );
}

function _InlineMusicBoxIdentity({ current }: { current: MusicBoxQueueItem | null }) {
  return (
    <Column mainAxisSize="min" mainAxisAlignment="center" crossAxisAlignment="stretch">
      <OverflowMarqueeText
        trackKey="live-control:music-title-marquee-track"
        text={current?.title ?? '未在播放'}
        style={{
          ...UiDefaultTextStyle,
          color: current == null ? UiColors.textMuted : UiColors.text,
          fontSize: 12,
          fontWeight: 600,
        }}
      />
      <Text
        maxLines={1}
        overflow="ellipsis"
        style={{
          ...UiDefaultTextStyle,
          color: UiColors.textSecondary,
          fontSize: 10,
          fontWeight: 600,
        }}
      >
        {current != null && current.artist.length > 0 ? current.artist : '点一首歌开始播放'}
      </Text>
    </Column>
  );
}

/**
 * A hairline server-authoritative progress track that hugs the bottom edge of
 * the inline music box. No time labels — just a 2px bar reflecting the
 * snapshot's reported position, set on a subtly darker groove so the unplayed
 * remainder still reads against the control bar.
 *
 * PORT: Material's determinate `LinearProgressIndicator(minHeight: 3)` is
 * drawn directly (track + value bar).
 */
function _InlineProgress({ state }: { state: MusicBoxState }) {
  const progress = music_box_display.musicBoxProgress(state);
  const fraction = _clampDouble(progress.fraction, 0.0, 1.0);
  return (
    <div
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={1}
      aria-valuenow={fraction}
      style={{ position: 'relative', height: 3, backgroundColor: UiColors.surfacePressed, overflow: 'hidden' }}
    >
      <div
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          bottom: 0,
          width: `${fraction * 100}%`,
          backgroundColor: UiColors.accent,
        }}
      />
    </div>
  );
}
