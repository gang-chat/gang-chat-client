// Port of `lib/src/settings/settings_audio_widgets.dart` (a `part of
// 'settings_page.dart'`): the voice section's device lists, volume / test
// panels with level meters and the screen-share quality picker. Part-private
// widgets are exported with their leading underscore (see
// docs/js-port-conventions.md).

import { useRef, useState, type CSSProperties } from 'react';

import {
  audioDeviceBusy,
  audioDeviceLabels,
  isSameAudioDevice,
} from '@gang-chat/core/app/audio_device_display';
import {
  AudioDeviceInfo,
  audioDeviceInfoId,
  audioDeviceInfoKind,
  audioDeviceInfoLabel,
} from '@gang-chat/core/app/audio_device_info';
import {
  activeAudioLevelSegmentCount,
  audioLevelSegmentCount,
  boostedAudioVolumePercentText,
} from '@gang-chat/core/app/audio_levels';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import {
  androidScreenShareFrameRateCap,
  desktopScreenShareFrameRateCap,
  normalizedScreenShareFrameRate,
  normalizedScreenShareMaxHeight,
  screenShareFrameRateLabel,
  screenShareFrameRateOptions,
  screenShareHeightOptions,
  screenShareResolutionLabel,
} from '@gang-chat/core/live/screen_share_quality';

import { EdgeInsets } from '../lib/edge_insets';
import { Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { useElementSize } from '../lib/use_element_size';
import { Button, ButtonTone } from '../ui/button';
import { Icon, Icons, type IconData } from '../ui/icon';
import { PressableSurface } from '../ui/surface';
import { UiSwitch } from '../ui/switch';
import { UiColors, UiSpacing, withAlpha } from '../ui/tokens';
import { _borderColor, _cyan, _primaryDark, _primaryDarkLow, _textMuted, _textPrimary, _textSecondary } from './settings_page';
import { _SettingsEmptyState } from './settings_profile_widgets';

/**
 * Space between the bordered single-choice rows (audio devices, screen-share
 * resolution / frame rate). The Flutter layout stacked them edge to edge.
 */
const _settingsOptionRowGap = UiSpacing.sm;

// ---------------------------------------------------------------------------
// _DeviceSection

export interface _DeviceSectionProps {
  title: string;
  icon: IconData;
  devices: readonly AudioDeviceInfo[];
  selectedDevice: AudioDeviceInfo | null;
  busyDeviceId: string | null;
  emptyText: string;
  fallbackLabel: string;
  onSelect: (device: AudioDeviceInfo) => void;
}

export function _DeviceSection({
  title,
  icon,
  devices,
  selectedDevice,
  busyDeviceId,
  emptyText,
  fallbackLabel,
  onSelect,
}: _DeviceSectionProps) {
  const labels = audioDeviceLabels(devices, {
    fallbackLabel,
    labelOf: audioDeviceInfoLabel,
    deviceIdOf: audioDeviceInfoId,
  });
  return (
    <Column crossAxisAlignment="start">
      <Row>
        <Icon icon={icon} color={_cyan} size={18} />
        <SizedBox width={9} />
        <Text style={{ color: _textPrimary, fontSize: 15, fontWeight: 600 }}>{title}</Text>
      </Row>
      <SizedBox height={12} />
      {devices.length === 0 ? (
        <_EmptyDeviceRow text={emptyText} />
      ) : (
        // The rows ask for `width: double.infinity`, so the column is as
        // wide as the panel. The bordered rows are spaced apart so each
        // device reads as its own choice.
        <Column crossAxisAlignment="stretch" gap={_settingsOptionRowGap} style={{ alignSelf: 'stretch' }}>
          {devices.map((device, index) => (
            <_DeviceRow
              key={`${device.kind}:${device.deviceId}:${index}`}
              device={device}
              label={labels[index]!}
              selected={isSameAudioDevice(selectedDevice, device, {
                kindOf: audioDeviceInfoKind,
                deviceIdOf: audioDeviceInfoId,
              })}
              busy={audioDeviceBusy(device, busyDeviceId, {
                kindOf: audioDeviceInfoKind,
                deviceIdOf: audioDeviceInfoId,
              })}
              onTap={() => onSelect(device)}
            />
          ))}
        </Column>
      )}
    </Column>
  );
}

// ---------------------------------------------------------------------------
// _AudioControlPanel

export interface _AudioControlPanelProps {
  title: string;
  icon: IconData;
  volume: number;
  level: number;
  testing: boolean;
  testTooltip: string;
  disabled: boolean;
  onVolumeChanged: (value: number) => void;
  onToggleTest: () => void;
  testDisabled?: boolean;
  /** The 增强 switch (gain × 1.5); omitted when the volume cannot be boosted. */
  boost?: boolean;
  onBoostChanged?: (value: boolean) => void;
}

export function _AudioControlPanel({
  title,
  icon,
  volume,
  level,
  testing,
  testTooltip,
  disabled,
  onVolumeChanged,
  onToggleTest,
  testDisabled = false,
  boost = false,
  onBoostChanged,
}: _AudioControlPanelProps) {
  return (
    <Column crossAxisAlignment="start">
      <Row style={{ alignSelf: 'stretch' }}>
        <Icon icon={icon} color={_cyan} size={18} />
        <SizedBox width={9} />
        <Expanded>
          <Text maxLines={1} style={{ color: _textPrimary, fontSize: 15, fontWeight: 600 }}>
            {title}
          </Text>
        </Expanded>
        {onBoostChanged != null ? (
          <>
            <Text softWrap={false} style={{ color: boost ? _textPrimary : _textSecondary, fontSize: 12, fontWeight: 600 }}>
              增强 +50%
            </Text>
            <SizedBox width={8} />
            <UiSwitch
              value={boost}
              onChanged={disabled ? null : onBoostChanged}
              tooltip="在当前音量上再放大 50%"
              aria-label={`${title}增强`}
            />
            <SizedBox width={14} />
          </>
        ) : null}
        <Text
          softWrap={false}
          style={{ color: _textSecondary, fontSize: 13, fontWeight: 600, minWidth: 38, textAlign: 'right' }}
        >
          {boostedAudioVolumePercentText(volume, boost)}
        </Text>
      </Row>
      <SizedBox height={8} />
      <Row style={{ alignSelf: 'stretch' }}>
        <Expanded>
          <_VolumeSlider value={volume} onChanged={disabled ? null : onVolumeChanged} aria-label={title} />
        </Expanded>
      </Row>
      <SizedBox height={8} />
      <Row style={{ alignSelf: 'stretch' }}>
        <SizedBox width={96} style={{ display: 'flex', flexDirection: 'column' }}>
          <Button
            onPressed={disabled || testDisabled ? null : onToggleTest}
            tooltip={testTooltip}
            height={42}
            width={Infinity}
            tone={testing ? ButtonTone.primary : ButtonTone.neutral}
            selected={testing}
            padding={EdgeInsets.symmetric({ horizontal: 8 })}
          >
            <Text maxLines={1}>{testing ? '停止测试' : '测试'}</Text>
          </Button>
        </SizedBox>
        <SizedBox width={10} />
        <Expanded>
          <_LevelMeter level={level} active={testing} />
        </Expanded>
      </Row>
    </Column>
  );
}

// ---------------------------------------------------------------------------
// The Material `Slider` the panel themed (SliderTheme: active accent,
// inactive border, text-coloured thumb, accent 14% overlay, 4px track).
//
// PORT: the kit has no Material slider (its `UiSlider` is the flat Signal
// slider), so this mirrors the Material geometry locally, like
// `ui/avatar_crop_dialog.tsx` does for its zoom slider: a 48px-tall control
// whose track stops at the overlay radius, a 2px-taller active segment and
// a 20px thumb.

const _sliderHeight = 48;
const _sliderTrackInset = 24; // overlay radius: the track stops at the overlay's centre
const _sliderTrackHeight = 4;
const _sliderThumbRadius = 10;
const _sliderOverlayRadius = 24;

function _clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function _VolumeSlider({
  value,
  onChanged,
  'aria-label': ariaLabel,
}: {
  value: number;
  onChanged: ((value: number) => void) | null;
  'aria-label'?: string;
}) {
  const ref = useRef<HTMLDivElement | null>(null);
  const [hovered, setHovered] = useState(false);
  const [dragPointer, setDragPointer] = useState<number | null>(null);
  const enabled = onChanged != null;
  // Slider(min: 0, max: 1).
  const fraction = _clamp(value, 0, 1);

  const emit = (clientX: number) => {
    const node = ref.current;
    if (node == null || onChanged == null) return;
    const rect = node.getBoundingClientRect();
    const travel = rect.width - _sliderTrackInset * 2;
    if (travel <= 0) return;
    onChanged(_clamp((clientX - rect.left - _sliderTrackInset) / travel, 0, 1));
  };

  const activeTrack = enabled ? _cyan : withAlpha(UiColors.text, 0.38);
  const inactiveTrack = enabled ? _borderColor : withAlpha(UiColors.text, 0.12);
  const thumbColor = enabled ? _textPrimary : withAlpha(UiColors.text, 0.38);
  const showOverlay = enabled && (hovered || dragPointer != null);
  const thumbLeft = `calc(${_sliderTrackInset}px + (100% - ${_sliderTrackInset * 2}px) * ${fraction})`;

  const trackBase: CSSProperties = { position: 'absolute', top: '50%', transform: 'translateY(-50%)' };
  return (
    <div
      ref={ref}
      role="slider"
      aria-label={ariaLabel}
      aria-valuemin={0}
      aria-valuemax={1}
      aria-valuenow={fraction}
      aria-disabled={!enabled}
      onPointerEnter={(event) => {
        if (event.pointerType === 'mouse') setHovered(true);
      }}
      onPointerLeave={(event) => {
        if (event.pointerType === 'mouse') setHovered(false);
      }}
      onPointerDown={(event) => {
        if (!enabled || event.button !== 0) return;
        try {
          event.currentTarget.setPointerCapture(event.pointerId);
        } catch {
          // Capture is best effort.
        }
        setDragPointer(event.pointerId);
        emit(event.clientX);
      }}
      onPointerMove={(event) => {
        if (dragPointer !== event.pointerId) return;
        emit(event.clientX);
      }}
      onPointerUp={(event) => {
        if (dragPointer === event.pointerId) setDragPointer(null);
      }}
      onPointerCancel={(event) => {
        if (dragPointer === event.pointerId) setDragPointer(null);
      }}
      style={{
        position: 'relative',
        height: _sliderHeight,
        width: '100%',
        cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        touchAction: 'none',
      }}
    >
      {/* Active track: 2px taller than the inactive one, rounded at its start. */}
      <div
        style={{
          ...trackBase,
          left: _sliderTrackInset,
          width: `calc((100% - ${_sliderTrackInset * 2}px) * ${fraction})`,
          height: _sliderTrackHeight + 2,
          borderRadius: `${(_sliderTrackHeight + 2) / 2}px 0 0 ${(_sliderTrackHeight + 2) / 2}px`,
          backgroundColor: activeTrack,
        }}
      />
      <div
        style={{
          ...trackBase,
          left: thumbLeft,
          right: _sliderTrackInset,
          height: _sliderTrackHeight,
          borderRadius: `0 ${_sliderTrackHeight / 2}px ${_sliderTrackHeight / 2}px 0`,
          backgroundColor: inactiveTrack,
        }}
      />
      <div
        style={{
          position: 'absolute',
          top: '50%',
          left: thumbLeft,
          width: _sliderOverlayRadius * 2,
          height: _sliderOverlayRadius * 2,
          borderRadius: '50%',
          transform: 'translate(-50%, -50%)',
          backgroundColor: withAlpha(_cyan, 0.14),
          opacity: showOverlay ? 1 : 0,
          transition: 'opacity 100ms linear',
          pointerEvents: 'none',
        }}
      />
      <div
        style={{
          position: 'absolute',
          top: '50%',
          left: thumbLeft,
          width: _sliderThumbRadius * 2,
          height: _sliderThumbRadius * 2,
          borderRadius: '50%',
          transform: 'translate(-50%, -50%)',
          backgroundColor: thumbColor,
          boxShadow: enabled
            ? `0 ${dragPointer != null ? 3 : 1}px ${dragPointer != null ? 6 : 2}px ${withAlpha(UiColors.black, 0.4)}`
            : 'none',
          pointerEvents: 'none',
        }}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------
// _LevelMeter

export function _LevelMeter({ level, active }: { level: number; active: boolean }) {
  // LayoutBuilder inside the padding: measure the row, which spans the
  // padded content box.
  const ref = useRef<HTMLDivElement | null>(null);
  const width = useElementSize(ref)?.width ?? 0;
  const segmentCount = audioLevelSegmentCount(width);
  const activeCount = activeAudioLevelSegmentCount({ level, active, segmentCount });
  return (
    <SizedBox
      height={52}
      style={{ backgroundColor: _primaryDark, padding: '8px 10px', display: 'flex', flexDirection: 'column', alignSelf: 'stretch' }}
    >
      <Row ref={ref} mainAxisAlignment="spaceBetween" style={{ flex: '1 1 0' }}>
        {Array.from({ length: segmentCount }, (_, i) => (
          <div
            key={i}
            style={{
              width: 5,
              height: 22,
              flexShrink: 0,
              backgroundColor: i < activeCount ? _cyan : UiColors.surfaceHover,
              borderRadius: 3,
              transition: 'background-color 90ms linear',
            }}
          />
        ))}
      </Row>
    </SizedBox>
  );
}

// ---------------------------------------------------------------------------
// _ScreenShareQualitySection

export interface _ScreenShareQualitySectionProps {
  selectedHeight: number;
  selectedFrameRate: number;
  onHeightSelected: (height: number) => void;
  onFrameRateSelected: (frameRate: number) => void;
  remoteUnavailable?: boolean;
}

export function _ScreenShareQualitySection({
  selectedHeight,
  selectedFrameRate,
  onHeightSelected,
  onFrameRateSelected,
  remoteUnavailable = false,
}: _ScreenShareQualitySectionProps) {
  const selectedResolution = normalizedScreenShareMaxHeight(selectedHeight);
  const platformFrameRateCap =
    !HostPlatform.isWeb && HostPlatform.isAndroid ? androidScreenShareFrameRateCap : desktopScreenShareFrameRateCap;
  const selectedFps = normalizedScreenShareFrameRate(selectedFrameRate, { maxFrameRate: platformFrameRateCap });
  const subheadingStyle: CSSProperties = { color: _textSecondary, fontSize: 13, fontWeight: 600 };
  return (
    <Column crossAxisAlignment="start">
      <Row>
        <Icon icon={Icons.screen_share_outlined} color={_cyan} size={18} />
        <SizedBox width={9} />
        <Text style={{ color: _textPrimary, fontSize: 15, fontWeight: 600 }}>屏幕共享画质</Text>
      </Row>
      <SizedBox height={8} />
      <Text style={{ color: _textSecondary, fontSize: 13, lineHeight: 1.4 }}>
        分辨率、帧率和码率会一起调整；较低档位可以显著节省上行带宽。
      </Text>
      <SizedBox height={12} />
      {remoteUnavailable ? (
        <_SettingsEmptyState text="该设置仅保存在用户设备，无法远程读取" />
      ) : (
        <Column crossAxisAlignment="start" style={{ alignSelf: 'stretch' }}>
          <Text style={subheadingStyle}>分辨率</Text>
          <SizedBox height={6} />
          <Column crossAxisAlignment="start" gap={_settingsOptionRowGap} style={{ alignSelf: 'stretch' }}>
            {screenShareHeightOptions.map((height) => (
              <_ScreenShareResolutionRow
                key={`height-${height}`}
                label={screenShareResolutionLabel(height)}
                selected={height === selectedResolution}
                onTap={() => onHeightSelected(height)}
              />
            ))}
          </Column>
          <SizedBox height={12} />
          <Text style={subheadingStyle}>帧率</Text>
          <SizedBox height={6} />
          <Column crossAxisAlignment="start" gap={_settingsOptionRowGap} style={{ alignSelf: 'stretch' }}>
            {screenShareFrameRateOptions
              .filter((frameRate) => frameRate <= platformFrameRateCap)
              .map((frameRate) => (
                <_ScreenShareResolutionRow
                  key={`fps-${frameRate}`}
                  label={screenShareFrameRateLabel(frameRate)}
                  selected={frameRate === selectedFps}
                  unselectedIcon={Icons.speed_outlined}
                  onTap={() => onFrameRateSelected(frameRate)}
                />
              ))}
          </Column>
        </Column>
      )}
    </Column>
  );
}

// ---------------------------------------------------------------------------
// _ScreenShareResolutionRow

export interface _ScreenShareResolutionRowProps {
  label: string;
  selected: boolean;
  onTap: () => void;
  unselectedIcon?: IconData;
}

export function _ScreenShareResolutionRow({
  label,
  selected,
  onTap,
  unselectedIcon = Icons.high_quality_outlined,
}: _ScreenShareResolutionRowProps) {
  return (
    <PressableSurface
      onPressed={onTap}
      height={50}
      width={Infinity}
      selected={selected}
      backgroundColor={_primaryDarkLow}
      selectedBackgroundColor={UiColors.selected}
      pressedBackgroundColor={_primaryDark}
      borderColor={_borderColor}
      selectedBorderColor={UiColors.selectedBorder}
      padding={EdgeInsets.symmetric({ horizontal: 14 })}
      // `width: double.infinity`: fill the column.
      style={{ alignSelf: 'stretch' }}
    >
      <Row>
        <Expanded>
          <Text maxLines={1} style={{ color: selected ? _textPrimary : _textSecondary, fontSize: 14, fontWeight: 600 }}>
            {label}
          </Text>
        </Expanded>
        {selected ? (
          <Icon icon={Icons.check} color={_cyan} size={18} />
        ) : (
          <Icon icon={unselectedIcon} color={_textMuted} size={18} />
        )}
      </Row>
    </PressableSurface>
  );
}

// ---------------------------------------------------------------------------
// _DeviceRow

export interface _DeviceRowProps {
  device: AudioDeviceInfo;
  label: string;
  selected: boolean;
  busy: boolean;
  onTap: () => void;
}

export function _DeviceRow({ device, label, selected, busy, onTap }: _DeviceRowProps) {
  return (
    <PressableSurface
      onPressed={busy ? null : onTap}
      height={50}
      width={Infinity}
      selected={selected}
      backgroundColor={_primaryDarkLow}
      selectedBackgroundColor={UiColors.selected}
      pressedBackgroundColor={_primaryDark}
      borderColor={_borderColor}
      selectedBorderColor={UiColors.selectedBorder}
      padding={EdgeInsets.symmetric({ horizontal: 14 })}
      // `width: double.infinity`: fill the column.
      style={{ alignSelf: 'stretch' }}
    >
      <Row>
        <Expanded>
          <Text
            maxLines={1}
            style={{
              color: selected ? _textPrimary : _textSecondary,
              fontSize: 14,
              fontWeight: selected ? 600 : 600,
            }}
          >
            {label}
          </Text>
        </Expanded>
        {busy ? (
          <CircularProgressIndicator size={16} strokeWidth={2} color={_cyan} />
        ) : selected ? (
          <Icon icon={Icons.check} color={_cyan} size={18} />
        ) : (
          <Icon icon={device.kind === 'audioinput' ? Icons.mic_none : Icons.volume_up} color={_textMuted} size={18} />
        )}
      </Row>
    </PressableSurface>
  );
}

// ---------------------------------------------------------------------------
// _EmptyDeviceRow

export function _EmptyDeviceRow({ text }: { text: string }) {
  return (
    // SizedBox(height: 50) > Align(centerLeft) > Padding(horizontal: 14).
    <SizedBox
      height={50}
      style={{ alignSelf: 'stretch', display: 'flex', flexDirection: 'row', alignItems: 'center', padding: '0 14px' }}
    >
      <Text style={{ color: _textMuted, fontSize: 13, fontWeight: 600 }}>{text}</Text>
    </SizedBox>
  );
}
