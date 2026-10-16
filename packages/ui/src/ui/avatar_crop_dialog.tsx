import {
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
  type PointerEvent as ReactPointerEvent,
  type WheelEvent as ReactWheelEvent,
} from 'react';

import { Center, Column, Expanded, Positioned, Row, SizedBox, Stack, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { ViewState, useViewState } from '../lib/view_state';
import { ButtonIcon, ButtonTone } from './button';
import { ResponsiveDialogAction, ResponsiveDialogActionBar, showFloatingErrorNotice } from './feedback';
import { Icon, Icons } from './icon';
import { useRoute } from './overlay';
import { UiColors, UiRadii, withAlpha } from './tokens';

/**
 * Port of `lib/src/ui/avatar_crop_dialog.dart`. Show it with
 * `showDialog<Uint8Array>(() => <AvatarCropDialog bytes={bytes} />, { barrierDismissible: false })`;
 * it closes with the cropped 512×512 PNG bytes, or `undefined` when cancelled.
 *
 * The source is decoded into an `<img>` (object URL), the work area is moved
 * with DOM pointer / wheel events, and the crop is rendered through a
 * `<canvas>` (Flutter: `PictureRecorder` + `drawImageRect` + `toByteData`).
 */
export interface AvatarCropDialogProps {
  bytes: Uint8Array;
  title?: string;
}

interface _Offset {
  dx: number;
  dy: number;
}

const _workSize = 320;
const _frameSize = 280;
const _outputSize = 512;
const _minZoom = 0.25;
const _maxZoom = 4.0;

function _clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/** A decoded source image (`ui.Image`): the `<img>` plus its object URL. */
interface _DecodedImage {
  element: HTMLImageElement;
  url: string;
  width: number;
  height: number;
}

class _AvatarCropDialogState extends ViewState<AvatarCropDialogProps> {
  _image: _DecodedImage | null = null;
  _imageError: unknown = null;
  _baseScale = 1;
  _zoom = 1;
  _offset: _Offset = { dx: 0, dy: 0 };
  _rendering = false;
  _dragging = false;
  _dragPointer: number | null = null;
  _lastDragPosition: { x: number; y: number } | null = null;
  /** `Navigator.of(context)`; refreshed on every build. */
  _close: (result?: Uint8Array) => void = () => {};

  override initState(): void {
    void this._decodeImage().then(
      (image) => {
        if (!this.mounted) {
          URL.revokeObjectURL(image.url);
          return;
        }
        this.setState(() => {
          this._image = image;
        });
      },
      (error: unknown) => {
        if (!this.mounted) return;
        this.setState(() => {
          this._imageError = error;
        });
      },
    );
  }

  async _decodeImage(): Promise<_DecodedImage> {
    const url = URL.createObjectURL(new Blob([this.props.bytes.slice()]));
    const element = new Image();
    element.decoding = 'async';
    element.src = url;
    try {
      await element.decode();
    } catch (error) {
      URL.revokeObjectURL(url);
      throw error;
    }
    const image = { element, url, width: element.naturalWidth, height: element.naturalHeight };
    this._baseScale = Math.max(_frameSize / image.width, _frameSize / image.height);
    return image;
  }

  override dispose(): void {
    if (this._image != null) URL.revokeObjectURL(this._image.url);
  }

  _handleWheel = (event: ReactWheelEvent<HTMLDivElement>, image: _DecodedImage): void => {
    if (event.deltaY === 0) return;
    const direction = event.deltaY < 0 ? 1 : -1;
    this._setZoom(_clamp(this._zoom + direction * 0.1, _minZoom, _maxZoom), image);
  };

  _startDrag = (event: ReactPointerEvent<HTMLDivElement>, _image: _DecodedImage): void => {
    if (this._rendering) return;
    if (event.pointerType === 'mouse' && event.buttons !== 1) return;
    try {
      event.currentTarget.setPointerCapture(event.pointerId);
    } catch {
      // Capture is best effort.
    }
    this._lastDragPosition = { x: event.clientX, y: event.clientY };
    this.setState(() => {
      this._dragging = true;
      this._dragPointer = event.pointerId;
    });
  };

  _moveDrag = (event: ReactPointerEvent<HTMLDivElement>, image: _DecodedImage): void => {
    if (!this._dragging || this._dragPointer !== event.pointerId) return;
    const last = this._lastDragPosition ?? { x: event.clientX, y: event.clientY };
    this._lastDragPosition = { x: event.clientX, y: event.clientY };
    const delta = { dx: event.clientX - last.x, dy: event.clientY - last.y };
    if (delta.dx === 0 && delta.dy === 0) return;
    this.setState(() => {
      this._offset = this._clampOffset({ dx: this._offset.dx + delta.dx, dy: this._offset.dy + delta.dy }, image);
    });
  };

  _endDrag = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (!this._dragging || this._dragPointer !== event.pointerId) return;
    this._lastDragPosition = null;
    this.setState(() => {
      this._dragging = false;
      this._dragPointer = null;
    });
  };

  _setZoom(value: number, image: _DecodedImage): void {
    this.setState(() => {
      this._zoom = _clamp(value, _minZoom, _maxZoom);
      this._offset = this._clampOffset(this._offset, image);
    });
  }

  _zoomSliderValue(): number {
    if (this._zoom <= 1) {
      const normalized = (this._zoom - _minZoom) / (1 - _minZoom);
      return _clamp(normalized * 0.5, 0, 0.5);
    }
    const normalized = (this._zoom - 1) / (_maxZoom - 1);
    return _clamp(0.5 + normalized * 0.5, 0.5, 1);
  }

  _setZoomFromSlider(value: number, image: _DecodedImage): void {
    if (value <= 0.5) {
      this._setZoom(_minZoom + (value / 0.5) * (1 - _minZoom), image);
      return;
    }
    this._setZoom(1 + ((value - 0.5) / 0.5) * (_maxZoom - 1), image);
  }

  _adjustZoom(delta: number, image: _DecodedImage): void {
    this._setZoom(_clamp(this._zoom + delta, _minZoom, _maxZoom), image);
  }

  _clampOffset(value: _Offset, image: _DecodedImage): _Offset {
    const displayWidth = image.width * this._baseScale * this._zoom;
    const displayHeight = image.height * this._baseScale * this._zoom;
    const maxX = Math.abs(displayWidth - _frameSize) / 2;
    const maxY = Math.abs(displayHeight - _frameSize) / 2;
    return { dx: _clamp(value.dx, -maxX, maxX), dy: _clamp(value.dy, -maxY, maxY) };
  }

  _confirm = async (): Promise<void> => {
    const image = this._image;
    if (image == null || this._rendering) return;
    this.setState(() => {
      this._rendering = true;
    });
    try {
      const bytes = await this._renderCrop(image);
      if (!this.mounted) return;
      this._close(bytes);
    } catch {
      if (!this.mounted) return;
      this.setState(() => {
        this._rendering = false;
      });
      showFloatingErrorNotice('裁剪图片失败');
    }
  };

  async _renderCrop(image: _DecodedImage): Promise<Uint8Array> {
    const canvas = document.createElement('canvas');
    canvas.width = _outputSize;
    canvas.height = _outputSize;
    const context = canvas.getContext('2d');
    if (context == null) throw new Error('没有可用的图片数据');
    const outputScale = _outputSize / _frameSize;
    const scale = this._baseScale * this._zoom;
    const displayedWidth = image.width * scale;
    const displayedHeight = image.height * scale;
    const imageLeft = _workSize / 2 + this._offset.dx - displayedWidth / 2;
    const imageTop = _workSize / 2 + this._offset.dy - displayedHeight / 2;
    const cropLeft = (_workSize - _frameSize) / 2;
    const cropTop = (_workSize - _frameSize) / 2;
    // FilterQuality.high.
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = 'high';
    context.drawImage(
      image.element,
      0,
      0,
      image.width,
      image.height,
      (imageLeft - cropLeft) * outputScale,
      (imageTop - cropTop) * outputScale,
      displayedWidth * outputScale,
      displayedHeight * outputScale,
    );
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
    if (blob == null) throw new Error('没有可用的图片数据');
    return new Uint8Array(await blob.arrayBuffer());
  }
}

export function AvatarCropDialog(props: AvatarCropDialogProps) {
  const state = useViewState(() => new _AvatarCropDialogState(), props);
  const route = useRoute<Uint8Array>();
  state._close = route.close;
  const { title = '裁剪头像' } = props;
  const image = state._image;
  const rendering = state._rendering;
  const zoomPercent = Math.round(state._zoom * 100);

  return (
    <_CropDialogShell maxWidth={440}>
      <Row>
        <Expanded>
          <Text style={{ color: UiColors.text, fontSize: 18, fontWeight: 600 }}>{title}</Text>
        </Expanded>
        <ButtonIcon onPressed={rendering ? null : () => route.close()} tooltip="关闭" icon={<Icon icon={Icons.close} />} size={32} />
      </Row>
      <SizedBox height={16} />
      {state._imageError != null ? (
        <_CropError message="无法读取图片" />
      ) : image == null ? (
        <SizedBox height={180}>
          <Center style={{ height: '100%' }}>
            <_CircularProgressIndicator color={UiColors.accent} />
          </Center>
        </SizedBox>
      ) : (
        <>
          <Center>
            <Stack
              onWheel={(event) => state._handleWheel(event, image)}
              onPointerDown={(event) => state._startDrag(event, image)}
              onPointerMove={(event) => state._moveDrag(event, image)}
              onPointerUp={state._endDrag}
              onPointerCancel={state._endDrag}
              style={{
                width: _workSize,
                height: _workSize,
                flexShrink: 0,
                cursor: state._dragging ? SystemMouseCursors.grabbing : SystemMouseCursors.grab,
                touchAction: 'none',
                userSelect: 'none',
              }}
            >
              <Positioned fill style={{ backgroundColor: UiColors.background }} />
              <Positioned fill style={{ overflow: 'hidden' }}>
                <img
                  src={image.url}
                  alt=""
                  draggable={false}
                  style={{
                    position: 'absolute',
                    left: _workSize / 2 - (image.width * state._baseScale) / 2,
                    top: _workSize / 2 - (image.height * state._baseScale) / 2,
                    width: image.width * state._baseScale,
                    height: image.height * state._baseScale,
                    objectFit: 'contain',
                    transform: `translate(${state._offset.dx}px, ${state._offset.dy}px) scale(${state._zoom})`,
                    transformOrigin: 'center',
                    pointerEvents: 'none',
                  }}
                />
              </Positioned>
              <Positioned fill style={{ pointerEvents: 'none' }}>
                <_CropShade frameSize={_frameSize} size={_workSize} />
              </Positioned>
            </Stack>
          </Center>
          <SizedBox height={14} />
          <Row>
            <ButtonIcon
              onPressed={rendering ? null : () => state._adjustZoom(-0.15, image)}
              tooltip="缩小 15%"
              icon={<Icon icon={Icons.zoom_out} />}
              size={30}
            />
            <SizedBox width={8} />
            <Expanded>
              <_CropZoomSlider
                value={state._zoomSliderValue()}
                onChanged={rendering ? null : (value) => state._setZoomFromSlider(value, image)}
              />
            </Expanded>
            <SizedBox width={8} />
            <ButtonIcon
              onPressed={rendering ? null : () => state._adjustZoom(0.6, image)}
              tooltip="放大 60%"
              icon={<Icon icon={Icons.zoom_in} />}
              size={30}
            />
          </Row>
          <SizedBox height={6} />
          <Center>
            <Text style={{ color: UiColors.textSecondary, fontSize: 12, fontWeight: 600 }}>{`${zoomPercent}%`}</Text>
          </Center>
        </>
      )}
      <SizedBox height={16} />
      <ResponsiveDialogActionBar
        actions={[
          new ResponsiveDialogAction({ label: '取消', onPressed: rendering ? null : () => route.close() }),
          new ResponsiveDialogAction({
            label: '确定',
            icon: Icons.crop,
            tone: ButtonTone.primary,
            loading: rendering,
            onPressed: image == null || rendering ? null : () => void state._confirm(),
          }),
        ]}
      />
    </_CropDialogShell>
  );
}

/**
 * Port of the Material `Dialog` the Flutter dialog sat in: [UiColors.surfaceLow]
 * with an `lg` rounded border, 40 / 24 inset from the window edges and at
 * least 280 wide.
 */
function _CropDialogShell({ maxWidth, children }: { maxWidth: number; children: ReactNode }) {
  return (
    <Column
      crossAxisAlignment="stretch"
      style={{
        width: maxWidth,
        minWidth: 280,
        maxWidth: 'calc(100vw - 80px)',
        maxHeight: 'calc(100vh - 48px)',
        margin: '0 16px',
        backgroundColor: UiColors.surfaceLow,
        borderRadius: UiRadii.lg,
        border: `1px solid ${UiColors.border}`,
        // Material paints the shape's side over the child, so the content
        // inset stays 22 / 20 / 22 / 18 from the outer edge.
        padding: '19px 21px 17px',
        boxSizing: 'border-box',
      }}
    >
      {children}
    </Column>
  );
}

/** Port of `_CropShadePainter`: dims everything outside the round frame and outlines it. */
function _CropShade({ frameSize, size }: { frameSize: number; size: number }) {
  const r = frameSize / 2;
  const c = size / 2;
  const overlay = `M0 0H${size}V${size}H0Z M${c - r} ${c}a${r} ${r} 0 1 0 ${frameSize} 0a${r} ${r} 0 1 0 ${-frameSize} 0Z`;
  return (
    <svg width={size} height={size} style={{ display: 'block' }} aria-hidden="true">
      <path d={overlay} fill={withAlpha(UiColors.black, 0.34)} fillRule="evenodd" />
      <circle cx={c} cy={c} r={r} fill="none" stroke={UiColors.accent} strokeWidth={1.4} />
    </svg>
  );
}

function _CropError({ message }: { message: string }) {
  return (
    <div
      style={{
        backgroundColor: UiColors.dangerSurface,
        border: `1px solid ${UiColors.dangerBorder}`,
        // DecoratedBox border over the 12px padding.
        padding: 11,
        boxSizing: 'border-box',
      }}
    >
      <Text style={{ color: UiColors.danger, fontSize: 12 }}>{message}</Text>
    </div>
  );
}

// ---------------------------------------------------------------------------
// The Material `Slider` the dialog themed (active accent, inactive border,
// text-coloured thumb, accent 14% overlay, 4px track).

const _sliderHeight = 48;
const _sliderTrackInset = 24; // overlay radius: the track stops at the overlay's centre
const _sliderTrackHeight = 4;
const _sliderThumbRadius = 10;
const _sliderOverlayRadius = 24;

function _CropZoomSlider({ value, onChanged }: { value: number; onChanged: ((value: number) => void) | null }) {
  const ref = useRef<HTMLDivElement | null>(null);
  const [hovered, setHovered] = useState(false);
  const [dragPointer, setDragPointer] = useState<number | null>(null);
  const enabled = onChanged != null;
  const fraction = _clamp(value, 0, 1);

  const emit = (clientX: number) => {
    const node = ref.current;
    if (node == null || onChanged == null) return;
    const rect = node.getBoundingClientRect();
    const travel = rect.width - _sliderTrackInset * 2;
    if (travel <= 0) return;
    onChanged(_clamp((clientX - rect.left - _sliderTrackInset) / travel, 0, 1));
  };

  const activeTrack = enabled ? UiColors.accent : withAlpha(UiColors.text, 0.38);
  const inactiveTrack = enabled ? UiColors.border : withAlpha(UiColors.text, 0.12);
  const thumbColor = enabled ? UiColors.text : withAlpha(UiColors.text, 0.38);
  const showOverlay = enabled && (hovered || dragPointer != null);
  const thumbLeft = `calc(${_sliderTrackInset}px + (100% - ${_sliderTrackInset * 2}px) * ${fraction})`;

  const trackBase: CSSProperties = { position: 'absolute', top: '50%', transform: 'translateY(-50%)' };
  return (
    <div
      ref={ref}
      role="slider"
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
          backgroundColor: withAlpha(UiColors.accent, 0.14),
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
          boxShadow: enabled ? `0 ${dragPointer != null ? 3 : 1}px ${dragPointer != null ? 6 : 2}px ${withAlpha(UiColors.black, 0.4)}` : 'none',
          pointerEvents: 'none',
        }}
      />
    </div>
  );
}

/**
 * Indeterminate Material `CircularProgressIndicator` (36px, 4px stroke),
 * animated with the Web Animations API.
 */
function _CircularProgressIndicator({ color, size = 36, strokeWidth = 4 }: { color: string; size?: number; strokeWidth?: number }) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const circleRef = useRef<SVGCircleElement | null>(null);
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  useEffect(() => {
    const svg = svgRef.current;
    const circle = circleRef.current;
    if (svg == null || circle == null || typeof svg.animate !== 'function') return;
    const rotation = svg.animate([{ transform: 'rotate(0deg)' }, { transform: 'rotate(360deg)' }], {
      duration: 1400,
      iterations: Infinity,
      easing: 'linear',
    });
    const sweep = circle.animate(
      [
        { strokeDasharray: `${circumference * 0.01} ${circumference}`, strokeDashoffset: '0' },
        { strokeDasharray: `${circumference * 0.75} ${circumference}`, strokeDashoffset: `${-circumference * 0.1}` },
        { strokeDasharray: `${circumference * 0.75} ${circumference}`, strokeDashoffset: `${-circumference * 0.99}` },
      ],
      { duration: 1400, iterations: Infinity, easing: 'ease-in-out' },
    );
    return () => {
      rotation.cancel();
      sweep.cancel();
    };
  }, [circumference]);
  return (
    <svg ref={svgRef} width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="progressbar" style={{ display: 'block' }}>
      <circle
        ref={circleRef}
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke={color}
        strokeWidth={strokeWidth}
        strokeLinecap="butt"
        strokeDasharray={`${circumference * 0.75} ${circumference}`}
      />
    </svg>
  );
}
