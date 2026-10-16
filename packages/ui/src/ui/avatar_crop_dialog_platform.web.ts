import { useEffect, useRef, type RefObject } from 'react';
import type { GestureResponderHandlers, View } from 'react-native';

/**
 * Web (Electron / browser) half of the avatar crop dialog: decodes the source
 * bytes into an `<img>`, renders the crop through a `<canvas>`, and wires the
 * work area's wheel / pointer events straight to the DOM node (the Flutter
 * dialog used a raw `Listener` for the same events).
 *
 * The native counterpart (`avatar_crop_dialog_platform.ts`) exports the same
 * signatures.
 */

/** A decoded source image (`ui.Image` in the Dart dialog). */
export interface CropSourceImage {
  readonly width: number;
  readonly height: number;
  /** URI the React Native `Image` renders. */
  readonly uri: string;
  /** Releases the decoded image (`ui.Image.dispose`). */
  dispose(): void;
}

/** Destination rectangle of the whole source image on the output canvas. */
export interface CropRenderRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

const elements = new WeakMap<CropSourceImage, HTMLImageElement>();

/** Port of `ui.instantiateImageCodec(bytes)` + `getNextFrame()`. */
export async function decodeCropImage(bytes: Uint8Array): Promise<CropSourceImage> {
  const uri = URL.createObjectURL(new Blob([bytes.slice()]));
  const element = new window.Image();
  element.decoding = 'async';
  element.src = uri;
  try {
    await element.decode();
  } catch (error) {
    URL.revokeObjectURL(uri);
    throw error;
  }
  let disposed = false;
  const image: CropSourceImage = {
    width: element.naturalWidth,
    height: element.naturalHeight,
    uri,
    dispose() {
      if (disposed) return;
      disposed = true;
      URL.revokeObjectURL(uri);
    },
  };
  elements.set(image, element);
  return image;
}

/**
 * Draws [image] into an [outputSize]² transparent canvas at [dest] and returns
 * the PNG bytes (port of `PictureRecorder` + `drawImageRect` +
 * `toByteData(format: png)`).
 */
export async function renderCropPng(image: CropSourceImage, outputSize: number, dest: CropRenderRect): Promise<Uint8Array> {
  const element = elements.get(image);
  if (element == null) throw new Error('没有可用的图片数据');
  const canvas = document.createElement('canvas');
  canvas.width = outputSize;
  canvas.height = outputSize;
  const context = canvas.getContext('2d');
  if (context == null) throw new Error('没有可用的图片数据');
  // FilterQuality.high.
  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = 'high';
  context.drawImage(element, 0, 0, image.width, image.height, dest.left, dest.top, dest.width, dest.height);
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
  if (blob == null) throw new Error('没有可用的图片数据');
  return new Uint8Array(await blob.arrayBuffer());
}

/** Pointer kinds as Flutter's `PointerDeviceKind` names them. */
export type CropPointerKind = 'mouse' | 'touch' | 'stylus' | 'unknown';

export interface CropPointerDown {
  pointer: number;
  kind: CropPointerKind;
  /** Pressed-button bit mask (`PointerEvent.buttons`; primary = 1). */
  buttons: number;
}

export interface CropPointerGestureHandlers {
  /** A wheel notch (`PointerScrollEvent.scrollDelta.dy`). */
  onWheel(deltaY: number): void;
  onPointerDown(event: CropPointerDown): void;
  onPointerMove(pointer: number, dx: number, dy: number): void;
  /** Pointer up or cancel. */
  onPointerEnd(pointer: number): void;
}

export interface CropPointerGestures {
  /** Attach to the work-area `View`. */
  ref: RefObject<View | null>;
  /** Spread onto the same `View` (empty on the web). */
  panHandlers: GestureResponderHandlers;
}

function pointerKind(type: string): CropPointerKind {
  switch (type) {
    case 'mouse':
      return 'mouse';
    case 'touch':
      return 'touch';
    case 'pen':
      return 'stylus';
    default:
      return 'unknown';
  }
}

/**
 * Port of the dialog's `Listener(onPointerSignal / onPointerDown / Move / Up /
 * Cancel)`: DOM wheel and pointer listeners on the work area. Moves are
 * reported as deltas since the previous event of the same pointer, which is
 * what `PointerMoveEvent.delta` carried. The pointer is captured so a drag
 * keeps tracking after it leaves the 320×320 area, like a Flutter pointer.
 */
export function useCropPointerGestures(handlers: CropPointerGestureHandlers): CropPointerGestures {
  const ref = useRef<View | null>(null);
  const handlersRef = useRef(handlers);
  handlersRef.current = handlers;
  const attached = useRef<{ node: HTMLElement; detach: () => void } | null>(null);

  // The work area only mounts once the image has decoded, so (re)attach
  // whenever the ref's node changes rather than once on mount. Listener state
  // lives with the attachment, so re-renders mid-drag keep tracking.
  useEffect(() => {
    const node = ref.current as unknown as HTMLElement | null;
    if (attached.current?.node === node) return;
    attached.current?.detach();
    attached.current = null;
    if (node == null || typeof node.addEventListener !== 'function') return;
    attached.current = { node, detach: attachCropListeners(node, handlersRef) };
  });
  useEffect(
    () => () => {
      attached.current?.detach();
      attached.current = null;
    },
    [],
  );

  return { ref, panHandlers: {} };
}

function attachCropListeners(node: HTMLElement, handlersRef: { readonly current: CropPointerGestureHandlers }): () => void {
  const last = new Map<number, { x: number; y: number }>();

  const onWheel = (event: WheelEvent) => {
    if (event.deltaY === 0) return;
    event.preventDefault();
    handlersRef.current.onWheel(event.deltaY);
  };
  const onPointerDown = (event: PointerEvent) => {
    last.set(event.pointerId, { x: event.clientX, y: event.clientY });
    try {
      node.setPointerCapture(event.pointerId);
    } catch {
      // Capture is best effort (synthetic events cannot be captured).
    }
    handlersRef.current.onPointerDown({
      pointer: event.pointerId,
      kind: pointerKind(event.pointerType),
      buttons: event.buttons,
    });
  };
  const onPointerMove = (event: PointerEvent) => {
    const previous = last.get(event.pointerId);
    if (previous == null) return;
    last.set(event.pointerId, { x: event.clientX, y: event.clientY });
    const dx = event.clientX - previous.x;
    const dy = event.clientY - previous.y;
    if (dx === 0 && dy === 0) return;
    handlersRef.current.onPointerMove(event.pointerId, dx, dy);
  };
  const onPointerEnd = (event: PointerEvent) => {
    if (!last.delete(event.pointerId)) return;
    try {
      node.releasePointerCapture(event.pointerId);
    } catch {
      // Already released.
    }
    handlersRef.current.onPointerEnd(event.pointerId);
  };

  node.addEventListener('wheel', onWheel, { passive: false });
  node.addEventListener('pointerdown', onPointerDown);
  node.addEventListener('pointermove', onPointerMove);
  node.addEventListener('pointerup', onPointerEnd);
  node.addEventListener('pointercancel', onPointerEnd);
  // Keep touch drags from scrolling or zooming the page underneath.
  const previousTouchAction = node.style.touchAction;
  node.style.touchAction = 'none';
  return () => {
    node.removeEventListener('wheel', onWheel);
    node.removeEventListener('pointerdown', onPointerDown);
    node.removeEventListener('pointermove', onPointerMove);
    node.removeEventListener('pointerup', onPointerEnd);
    node.removeEventListener('pointercancel', onPointerEnd);
    node.style.touchAction = previousTouchAction;
  };
}
