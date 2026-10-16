import { useRef, type RefObject } from 'react';
import { Image, PanResponder, type GestureResponderHandlers, type View } from 'react-native';

/**
 * React Native half of the avatar crop dialog (see
 * `avatar_crop_dialog_platform.web.ts` for the web implementation; both export
 * the same signatures).
 *
 * The source bytes are shown through a `data:` URI and measured with
 * `Image.getSize`; drags come from a `PanResponder`.
 */

export interface CropSourceImage {
  readonly width: number;
  readonly height: number;
  /** URI the React Native `Image` renders. */
  readonly uri: string;
  /** Releases the decoded image (`ui.Image.dispose`). */
  dispose(): void;
}

export interface CropRenderRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

const _base64Alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

function _base64(bytes: Uint8Array): string {
  let out = '';
  let i = 0;
  for (; i + 2 < bytes.length; i += 3) {
    const n = (bytes[i]! << 16) | (bytes[i + 1]! << 8) | bytes[i + 2]!;
    out += _base64Alphabet[(n >> 18) & 63]! + _base64Alphabet[(n >> 12) & 63]! + _base64Alphabet[(n >> 6) & 63]! + _base64Alphabet[n & 63]!;
  }
  const rest = bytes.length - i;
  if (rest === 1) {
    const n = bytes[i]! << 16;
    out += `${_base64Alphabet[(n >> 18) & 63]!}${_base64Alphabet[(n >> 12) & 63]!}==`;
  } else if (rest === 2) {
    const n = (bytes[i]! << 16) | (bytes[i + 1]! << 8);
    out += `${_base64Alphabet[(n >> 18) & 63]!}${_base64Alphabet[(n >> 12) & 63]!}${_base64Alphabet[(n >> 6) & 63]!}=`;
  }
  return out;
}

function _sniffImageMimeType(bytes: Uint8Array): string {
  if (bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47) return 'image/png';
  if (bytes[0] === 0xff && bytes[1] === 0xd8) return 'image/jpeg';
  if (bytes[0] === 0x47 && bytes[1] === 0x49 && bytes[2] === 0x46) return 'image/gif';
  if (bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50) return 'image/webp';
  if (bytes[0] === 0x42 && bytes[1] === 0x4d) return 'image/bmp';
  return 'image/png';
}

export async function decodeCropImage(bytes: Uint8Array): Promise<CropSourceImage> {
  const uri = `data:${_sniffImageMimeType(bytes)};base64,${_base64(bytes)}`;
  const { width, height } = await new Promise<{ width: number; height: number }>((resolve, reject) => {
    Image.getSize(uri, (w, h) => resolve({ width: w, height: h }), (error) => reject(error ?? new Error('无法读取图片')));
  });
  return { width, height, uri, dispose() {} };
}

/**
 * PORT: React Native has no built-in raster canvas. Producing the cropped PNG
 * natively needs an image-manipulation module (for example
 * `expo-image-manipulator` or `@shopify/react-native-skia`); until the mobile
 * app adds one this rejects, and the dialog shows its "裁剪图片失败" notice.
 */
export async function renderCropPng(_image: CropSourceImage, _outputSize: number, _dest: CropRenderRect): Promise<Uint8Array> {
  throw new Error('PORT: native avatar cropping needs an image manipulation module');
}

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
  ref: RefObject<View | null>;
  panHandlers: GestureResponderHandlers;
}

/**
 * Drag tracking through a `PanResponder` (single pointer, id 0). There is no
 * wheel on touch devices; pinch zoom is not part of the Flutter dialog either.
 */
export function useCropPointerGestures(handlers: CropPointerGestureHandlers): CropPointerGestures {
  const ref = useRef<View | null>(null);
  const handlersRef = useRef(handlers);
  handlersRef.current = handlers;
  const previous = useRef({ dx: 0, dy: 0 });
  const responder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderTerminationRequest: () => false,
      onPanResponderGrant: () => {
        previous.current = { dx: 0, dy: 0 };
        handlersRef.current.onPointerDown({ pointer: 0, kind: 'touch', buttons: 1 });
      },
      onPanResponderMove: (_event, gesture) => {
        const dx = gesture.dx - previous.current.dx;
        const dy = gesture.dy - previous.current.dy;
        previous.current = { dx: gesture.dx, dy: gesture.dy };
        if (dx === 0 && dy === 0) return;
        handlersRef.current.onPointerMove(0, dx, dy);
      },
      onPanResponderRelease: () => handlersRef.current.onPointerEnd(0),
      onPanResponderTerminate: () => handlersRef.current.onPointerEnd(0),
    }),
  ).current;
  return { ref, panHandlers: responder.panHandlers };
}
