import {
  createContext,
  useContext,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react';

import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import { MediaCacheController, MediaCacheRequest } from '@gang-chat/core/app/media_cache_controller';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { Completer, Timer, unawaited } from '@gang-chat/core/lib/async';
import { ValueNotifier } from '@gang-chat/core/lib/change_notifier';
import type { Message, MessageAttachment } from '@gang-chat/core/protocol/models';
import {
  AndroidDisplayRotationService,
  counterDisplayRotationQuarterTurns,
  type AndroidDisplayRotationReader,
} from '@gang-chat/core/shell/android_display_rotation_service';
import { isAndroidTabletLogicalSize } from '@gang-chat/core/shell/android_form_factor';
import { AppOrientationController } from '@gang-chat/core/shell/app_orientation_controller';

import { Column, Expanded, Positioned, Row, SizedBox } from '../lib/layout';
import { kTouchSlop } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { useElementSize } from '../lib/use_element_size';
import { useListenable } from '../lib/use_listenable';
import { useViewState, ViewState } from '../lib/view_state';
import { ButtonIcon } from '../ui/button';
import { cachedMediaImageProvider } from '../ui/cached_asset_image';
import { showFloatingErrorNotice, showFloatingSuccessNotice } from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { useMediaCache } from '../ui/media_cache_scope';
import { pushRoute, type RouteHandle } from '../ui/overlay';
import { UiColors, argb } from '../ui/tokens';
import { FullScreenMediaOrientation } from '../shell/full_screen_media_orientation';

// ---------------------------------------------------------------------------
// ChatImagePreviewActionsScope.

interface _ChatImagePreviewActionsInherited {
  readonly actions: ChatImagePreviewActions;
  readonly captureBackdrop: ImagePreviewBackdropCapture;
}

const _ChatImagePreviewActionsContext = createContext<_ChatImagePreviewActionsInherited | null>(null);

export interface ChatImagePreviewActionsScopeProps {
  actions: ChatImagePreviewActions;
  children?: ReactNode;
}

/**
 * PORT: the Dart scope also wrapped its child in a `RepaintBoundary` so the
 * Android phone preview could freeze a snapshot of the UI behind it. The DOM
 * cannot rasterize its own page, so [captureBackdrop] always yields null and
 * the preview falls back to the regular (translucent) route.
 */
async function _captureBackdrop(): Promise<ImagePreviewBackdropSnapshot | null> {
  return null;
}

function ChatImagePreviewActionsScopeImpl({ actions, children }: ChatImagePreviewActionsScopeProps) {
  const value = useMemo<_ChatImagePreviewActionsInherited>(() => ({ actions, captureBackdrop: _captureBackdrop }), [actions]);
  return <_ChatImagePreviewActionsContext.Provider value={value}>{children}</_ChatImagePreviewActionsContext.Provider>;
}

/** Port of `ChatImagePreviewActionsScope.maybeOf(context)` (a hook). */
function useChatImagePreviewActions(): ChatImagePreviewActions | null {
  return useContext(_ChatImagePreviewActionsContext)?.actions ?? null;
}

export const ChatImagePreviewActionsScope = Object.assign(ChatImagePreviewActionsScopeImpl, {
  maybeOf: useChatImagePreviewActions,
  /** Port of `ChatImagePreviewActionsScope.captureBackdrop(context)` (the context is dropped). */
  captureBackdrop(): Promise<ImagePreviewBackdropSnapshot | null> {
    return _captureBackdrop();
  },
});

// ---------------------------------------------------------------------------
// Actions.

async function _noopWithName(_url: string, _suggestedName: string): Promise<void> {}

async function _noopWithUrl(_url: string): Promise<void> {}

/**
 * Callbacks for the full-screen image preview overlay, bundled so they can
 * travel from [ChatPane] down to each image bubble without threading several
 * separate parameters through every intermediate widget. The home shell wires
 * the implementations (download, save-as, clipboard, save-sticker).
 */
export class ChatImagePreviewActions {
  constructor(opts: {
    onDownload: (url: string, suggestedName: string) => Promise<void>;
    onSaveAs: (url: string, suggestedName: string) => Promise<void>;
    onCopyToClipboard: (url: string) => Promise<void>;
    onSaveSticker?: ((message: Message, attachment: MessageAttachment) => Promise<void>) | null;
    onSaveRoomSticker?: ((message: Message, attachment: MessageAttachment) => Promise<void>) | null;
    mediaCache?: MediaCacheController | null;
  }) {
    this.onDownload = opts.onDownload;
    this.onSaveAs = opts.onSaveAs;
    this.onCopyToClipboard = opts.onCopyToClipboard;
    this.onSaveSticker = opts.onSaveSticker ?? null;
    this.onSaveRoomSticker = opts.onSaveRoomSticker ?? null;
    this.mediaCache = opts.mediaCache ?? null;
  }

  /** A no-op bundle for tests and previews where the actions aren't exercised. */
  static disabled(): ChatImagePreviewActions {
    return new ChatImagePreviewActions({
      onDownload: _noopWithName,
      onSaveAs: _noopWithName,
      onCopyToClipboard: _noopWithUrl,
    });
  }

  /**
   * Save the image at [url] straight to the user's Downloads folder, using
   * [suggestedName] as the filename. Throws on failure.
   */
  readonly onDownload: (url: string, suggestedName: string) => Promise<void>;

  /** Save the image at [url] through a save-location picker. Throws on failure. */
  readonly onSaveAs: (url: string, suggestedName: string) => Promise<void>;

  /** Copy the image bytes at [url] to the system clipboard. Throws on failure. */
  readonly onCopyToClipboard: (url: string) => Promise<void>;

  /**
   * Save a sticker message's sticker into the user's personal stickers. Null
   * when the source isn't a savable sticker (e.g. a plain image attachment) or
   * the API is unavailable. Throws on failure.
   */
  readonly onSaveSticker: ((message: Message, attachment: MessageAttachment) => Promise<void>) | null;

  /**
   * Add a sticker message's sticker to the current room's sticker pack. Null
   * unless the source is a savable sticker and the current user can administer
   * the room. Throws on failure.
   */
  readonly onSaveRoomSticker: ((message: Message, attachment: MessageAttachment) => Promise<void>) | null;

  /** Optional disk cache used for preview rendering and image actions. */
  readonly mediaCache: MediaCacheController | null;
}

/**
 * Thrown by a preview action when the user cancels (e.g. dismisses the save-as
 * picker). The overlay treats it as a silent no-op rather than a failure.
 */
export class ImagePreviewActionCancelled extends Error {
  constructor() {
    super('ImagePreviewActionCancelled');
    this.name = 'ImagePreviewActionCancelled';
  }
}

/**
 * A frozen copy of the UI behind an Android image preview. [logicalSize]
 * preserves the portrait layout dimensions independently of later device
 * orientation changes.
 *
 * PORT: `dart:ui` `Image` → an image URL (object or data URL); [dispose]
 * revokes object URLs.
 */
export class ImagePreviewBackdropSnapshot {
  constructor(opts: { image: string; logicalSize: { width: number; height: number } }) {
    this.image = opts.image;
    this.logicalSize = opts.logicalSize;
  }

  readonly image: string;
  readonly logicalSize: { width: number; height: number };

  dispose(): void {
    if (this.image.startsWith('blob:')) URL.revokeObjectURL(this.image);
  }
}

export type ImagePreviewBackdropCapture = () => Promise<ImagePreviewBackdropSnapshot | null>;

export interface ChatImagePreviewStickerSource {
  readonly message: Message;
  readonly attachment: MessageAttachment;
}

/** Dart `TargetPlatform` of the preview, as `HostPlatform.operatingSystem`. */
type _PreviewPlatform = string;

const _routeTransitionDuration = 140;
const _routeReverseTransitionDuration = 120;
const _previewBarrierColor = argb(0xd1000000);
const _previewControlBackground = argb(0x66000000);

/**
 * Opens the full-screen image preview as an overlay route. [imageUrl] is the
 * already-resolved absolute URL to display. [suggestedName] seeds the
 * download/save-as filename. When [stickerSource] is non-null and
 * [actions.onSaveSticker] is provided, an "添加到我的表情包" action is shown.
 * (The Dart `BuildContext` argument is dropped.)
 */
export async function showChatImagePreview(opts: {
  imageUrl: string;
  suggestedName: string;
  actions: ChatImagePreviewActions;
  stickerSource?: ChatImagePreviewStickerSource | null;
  showActionBar?: boolean;
  forceSquare?: boolean;
  orientationController?: AppOrientationController | null;
  backdropCapture?: ImagePreviewBackdropCapture | null;
  displayRotationReader?: AndroidDisplayRotationReader | null;
}): Promise<void> {
  const {
    imageUrl,
    suggestedName,
    actions,
    stickerSource = null,
    showActionBar = true,
    forceSquare = false,
    orientationController = null,
    backdropCapture = null,
    displayRotationReader = null,
  } = opts;
  const previewPlatform: _PreviewPlatform = HostPlatform.operatingSystem;
  const useAndroidPhonePreview =
    previewPlatform === 'android' && !isAndroidTabletLogicalSize({ width: window.innerWidth, height: window.innerHeight });
  let frozenBackdrop: ImagePreviewBackdropSnapshot | null = null;
  if (useAndroidPhonePreview) {
    frozenBackdrop = await (backdropCapture?.() ?? ChatImagePreviewActionsScope.captureBackdrop());
  }
  const hasFrozenAndroidBackdrop = useAndroidPhonePreview && frozenBackdrop != null;
  const previewOrientationController = hasFrozenAndroidBackdrop ? (orientationController ?? new AppOrientationController()) : null;
  const activeDisplayRotationReader: AndroidDisplayRotationReader | null = hasFrozenAndroidBackdrop
    ? (displayRotationReader ??
      (() => {
        const service = new AndroidDisplayRotationService();
        return () => service.currentQuarterTurns();
      })())
    : null;

  const stateRef: { current: _ImagePreviewOverlayState | null } = { current: null };
  let route: RouteHandle<void> | null = null;
  try {
    route = pushRoute<void>(
      () => {
        const overlay = (
          <_ImagePreviewOverlay
            imageUrl={imageUrl}
            suggestedName={suggestedName}
            actions={actions}
            stickerSource={stickerSource}
            showActionBar={showActionBar}
            forceSquare={forceSquare}
            frozenBackdrop={frozenBackdrop}
            orientationController={previewOrientationController}
            previewPlatform={previewPlatform}
            displayRotationReader={activeDisplayRotationReader}
            animated={!hasFrozenAndroidBackdrop}
            stateRef={stateRef}
            onPop={() => route?.close()}
          />
        );
        if (!hasFrozenAndroidBackdrop) return overlay;
        return (
          <FullScreenMediaOrientation controller={previewOrientationController ?? undefined}>{overlay}</FullScreenMediaOrientation>
        );
      },
      {
        // The overlay draws (and fades) its own barrier; see _ImagePreviewOverlay.
        barrierColor: 'transparent',
        barrierDismissible: !hasFrozenAndroidBackdrop,
        dismissOnBack: true,
        // Escape / back runs the overlay's own close (fade out, orientation
        // restore) instead of removing the route at once.
        onWillPop: () => {
          const state = stateRef.current;
          if (state == null || !state.mounted) return true;
          unawaited(state._close());
          return false;
        },
      },
    );
    await route.result;
  } finally {
    frozenBackdrop?.dispose();
  }
}

interface _ImagePreviewOverlayProps {
  imageUrl: string;
  suggestedName: string;
  actions: ChatImagePreviewActions;
  stickerSource: ChatImagePreviewStickerSource | null;
  showActionBar: boolean;
  forceSquare: boolean;
  frozenBackdrop: ImagePreviewBackdropSnapshot | null;
  orientationController: AppOrientationController | null;
  previewPlatform: _PreviewPlatform;
  displayRotationReader: AndroidDisplayRotationReader | null;
  // DOM additions: route plumbing that Flutter's Navigator provided.
  animated: boolean;
  stateRef: { current: _ImagePreviewOverlayState | null };
  onPop: () => void;
  /** `MediaCacheScope.of(context)`, read by the component. */
  scopeCache?: MediaCacheController;
}

enum _PreviewAction {
  download = 'download',
  saveAs = 'saveAs',
  copy = 'copy',
  saveSticker = 'saveSticker',
  saveRoomSticker = 'saveRoomSticker',
}

interface _Size {
  width: number;
  height: number;
}

interface _Offset {
  x: number;
  y: number;
}

class _Rect {
  constructor(
    readonly left: number,
    readonly top: number,
    readonly right: number,
    readonly bottom: number,
  ) {}

  static fromLTWH(left: number, top: number, width: number, height: number): _Rect {
    return new _Rect(left, top, left + width, top + height);
  }

  get width(): number {
    return this.right - this.left;
  }

  get height(): number {
    return this.bottom - this.top;
  }

  get center(): _Offset {
    return { x: this.left + this.width / 2, y: this.top + this.height / 2 };
  }

  contains(point: _Offset): boolean {
    return point.x >= this.left && point.x < this.right && point.y >= this.top && point.y < this.bottom;
  }
}

interface _PreviewImageData {
  /** The URL the `<img>` loads (Dart `ImageProvider`). */
  readonly image: string;
  readonly size: _Size;
}

class _PreviewImageTransform {
  constructor(
    readonly scale = 1.0,
    readonly offset: _Offset = { x: 0, y: 0 },
  ) {}

  /** CSS form of `Matrix4.diagonal3Values(scale, scale, 1)..setTranslationRaw(dx, dy, 0)`. */
  get matrix(): string {
    return `matrix(${this.scale}, 0, 0, ${this.scale}, ${this.offset.x}, ${this.offset.y})`;
  }

  equals(other: _PreviewImageTransform): boolean {
    return other.scale === this.scale && other.offset.x === this.offset.x && other.offset.y === this.offset.y;
  }
}

class _ImagePreviewOverlayState extends ViewState<_ImagePreviewOverlayProps> {
  static readonly _controlsAutoHideDelay = 3000;
  static readonly _minImageScale = 0.25;
  static readonly _maxImageScale = 5.0;
  static readonly _wheelScaleBase = 1.25;
  static readonly _previewImageKey = 'chat-image-preview-url-image';

  readonly _imageTransform = new ValueNotifier<_PreviewImageTransform>(new _PreviewImageTransform());

  _previewImageData: Promise<_PreviewImageData> | null = null;
  _previewImageDataKey: string | null = null;
  _previewImageCache: MediaCacheController | null = null;
  /** FutureBuilder snapshot of [_previewImageData]. */
  _previewImageSnapshot: { data: _PreviewImageData | null; error: unknown } = { data: null, error: null };
  _gestureStartScale: number | null = null;
  _gestureStartContentFocal: _Offset | null = null;
  _busy: _PreviewAction | null = null;
  _closing = false;
  _routeCanPop = false;
  _controlsVisible = true;
  _controlsHideTimer: Timer | null = null;
  _portraitViewportCompleter: Completer<void> | null = null;
  _lastAndroidPreviewOrientation: 'portrait' | 'landscape' | null = null;
  _backdropQuarterTurns = 0;
  _displayRotationReadGeneration = 0;
  /** Route fade (DOM addition: Flutter's route animation). */
  _routeShown = false;
  _restoreFocus: HTMLElement | null = null;
  readonly _rootRef: { current: HTMLDivElement | null } = { current: null };

  get _canSaveSticker(): boolean {
    return this.widget.stickerSource != null && this.widget.actions.onSaveSticker != null;
  }

  get _canSaveRoomSticker(): boolean {
    return this.widget.stickerSource != null && this.widget.actions.onSaveRoomSticker != null;
  }

  // The Android route uses a semi-transparent cap over a frozen backdrop. Its
  // base border would otherwise show through as an extra horizontal line.

  get _isAndroidPreview(): boolean {
    return this.widget.previewPlatform === 'android';
  }

  get _usesImmersiveControls(): boolean {
    return this._isAndroidPreview || this.widget.previewPlatform === 'windows';
  }

  get _isPortraitViewport(): boolean {
    return window.innerHeight >= window.innerWidth;
  }

  override initState(): void {
    this.widget.stateRef.current = this;
    window.addEventListener('resize', this._didChangeMetrics);
    this._scheduleControlsAutoHide();
    unawaited(this._refreshBackdropRotation());
    this._lastAndroidPreviewOrientation = this._isPortraitViewport ? 'portrait' : 'landscape';
    this._ensurePreviewImageData();
    // Flutter's `Focus(autofocus: true)`: move keyboard focus off the page (the
    // composer) while the preview is up; the route restores it on close.
    this._restoreFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    this._rootRef.current?.focus({ preventScroll: true });
    if (this.widget.animated) {
      requestAnimationFrame(() => {
        if (!this.mounted || this._closing) return;
        this.setState(() => {
          this._routeShown = true;
        });
      });
    } else {
      this._routeShown = true;
    }
  }

  readonly _didChangeMetrics = (): void => {
    if (this.mounted && this._isPortraitViewport) {
      const completer = this._portraitViewportCompleter;
      if (completer != null && !completer.isCompleted) completer.complete();
    }
    unawaited(this._refreshBackdropRotation());
    this._didChangeOrientation();
  };

  async _refreshBackdropRotation(): Promise<void> {
    const reader = this.widget.displayRotationReader;
    if (reader == null) return;
    const generation = ++this._displayRotationReadGeneration;
    try {
      const displayQuarterTurns = await reader();
      if (!this.mounted || generation !== this._displayRotationReadGeneration) return;
      const nextQuarterTurns = counterDisplayRotationQuarterTurns(displayQuarterTurns);
      if (this._backdropQuarterTurns === nextQuarterTurns) return;
      this.setState(() => {
        this._backdropQuarterTurns = nextQuarterTurns;
      });
    } catch {
      // Preserve the last valid counter-rotation if the platform channel is
      // temporarily unavailable during Android's display transition.
    }
  }

  /** The Android part of Dart's `didChangeDependencies` (orientation changes). */
  _didChangeOrientation(): void {
    if (!this._isAndroidPreview) return;
    const orientation = this._isPortraitViewport ? 'portrait' : 'landscape';
    const previousOrientation = this._lastAndroidPreviewOrientation;
    this._lastAndroidPreviewOrientation = orientation;
    if (previousOrientation != null && previousOrientation !== orientation) {
      this._resetImageTransform();
      if (!this._closing) {
        this.setState(() => {
          this._controlsVisible = true;
        });
        this._scheduleControlsAutoHide();
      }
    }
  }

  override didUpdateProps(_oldWidget: _ImagePreviewOverlayProps): void {
    this._ensurePreviewImageData();
  }

  _ensurePreviewImageData(): void {
    const cache = this.widget.actions.mediaCache ?? this.widget.scopeCache ?? this._previewImageCache ?? new MediaCacheController();
    const key = [this.widget.imageUrl, this.widget.suggestedName].join('\n');
    if (this._previewImageDataKey === key && this._previewImageCache === cache) return;
    this._previewImageDataKey = key;
    this._previewImageCache = cache;
    const future = this._loadPreviewImageData(cache);
    this._previewImageData = future;
    this._previewImageSnapshot = { data: null, error: null };
    future.then(
      (data) => {
        if (!this.mounted || this._previewImageData !== future) return;
        this.setState(() => {
          this._previewImageSnapshot = { data, error: null };
        });
      },
      (error: unknown) => {
        if (!this.mounted || this._previewImageData !== future) return;
        this.setState(() => {
          this._previewImageSnapshot = { data: null, error: error ?? new Error('图片加载失败') };
        });
      },
    );
    this._resetImageTransform();
  }

  _resetImageTransform(): void {
    this._imageTransform.value = new _PreviewImageTransform();
    this._gestureStartScale = null;
    this._gestureStartContentFocal = null;
  }

  _scheduleControlsAutoHide(): void {
    if (!this._usesImmersiveControls || this._closing) return;
    this._controlsHideTimer?.cancel();
    this._controlsHideTimer = new Timer(_ImagePreviewOverlayState._controlsAutoHideDelay, () => {
      if (!this.mounted || this._closing || !this._controlsVisible) return;
      this.setState(() => {
        this._controlsVisible = false;
      });
    });
  }

  readonly _handlePreviewInteraction = (): void => {
    if (!this._usesImmersiveControls || this._closing) return;
    if (!this._controlsVisible) {
      this.setState(() => {
        this._controlsVisible = true;
      });
    }
    this._scheduleControlsAutoHide();
  };

  async _loadPreviewImageData(cache: MediaCacheController): Promise<_PreviewImageData> {
    const request = MediaCacheRequest.tryFromUrl({
      url: this.widget.imageUrl,
      filename: this.widget.suggestedName,
    });
    if (request == null) {
      throw new Error('图片地址无效');
    }
    // PORT: Dart read the bytes through [cache] (from memory on the web, from
    // the cached file natively) and decoded them for the intrinsic size. The
    // desktop host serves cached media itself, so the preview loads the
    // cache URL and the browser decodes it.
    const image = await cachedMediaImageProvider(cache, request);
    const size = await this._decodePreviewImageSize(image);
    return { image, size };
  }

  async _decodePreviewImageSize(url: string): Promise<_Size> {
    const image = new Image();
    image.decoding = 'async';
    const loaded = new Promise<void>((resolve, reject) => {
      image.onload = () => resolve();
      image.onerror = () => reject(new Error('图片解码失败'));
    });
    image.src = url;
    try {
      await image.decode();
    } catch {
      await loaded;
    }
    return { width: image.naturalWidth, height: image.naturalHeight };
  }

  async _run(action: _PreviewAction, task: () => Promise<void>, opts: { successMessage: string }): Promise<void> {
    if (this._busy != null) return;
    this.setState(() => {
      this._busy = action;
    });
    try {
      await task();
      if (!this.mounted) return;
      showFloatingSuccessNotice(opts.successMessage);
    } catch (error) {
      // User cancelled (e.g. closed the save-as picker); leave the overlay as
      // it was without showing success or error.
      if (error instanceof ImagePreviewActionCancelled) return;
      if (!this.mounted) return;
      showFloatingErrorNotice(userFacingErrorMessage(error, { fallback: '图片操作失败' }));
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._busy = null;
        });
      }
    }
  }

  readonly _close = async (): Promise<void> => {
    if (this._closing) return;
    this._closing = true;
    this._controlsHideTimer?.cancel();

    const orientationController = this.widget.orientationController;
    if (orientationController != null) {
      const restorePortrait = orientationController.restoresPortraitByDefault;
      try {
        await orientationController.restoreDefaultOrientation();
      } catch {
        // Keep the opaque preview in place even if the platform request fails;
        // it must never expose a landscape layout from the route underneath.
      }
      if (restorePortrait && this.mounted && !this._isPortraitViewport) {
        const completer = new Completer<void>();
        this._portraitViewportCompleter = completer;
        await completer.promise;
      }
      this._portraitViewportCompleter = null;
    }

    if (!this.mounted) return;
    if (orientationController != null) {
      this.setState(() => {
        this._routeCanPop = true;
      });
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
      if (!this.mounted) return;
    }
    // Navigator.maybePop: the route's reverse transition, then removal.
    this.setState(() => {
      this._routeShown = false;
    });
    if (this.widget.animated) {
      await new Promise<void>((resolve) => setTimeout(resolve, _routeReverseTransitionDuration));
    }
    if (!this.mounted) return;
    this.widget.onPop();
  };

  override dispose(): void {
    this._displayRotationReadGeneration += 1;
    window.removeEventListener('resize', this._didChangeMetrics);
    this._controlsHideTimer?.cancel();
    const completer = this._portraitViewportCompleter;
    if (completer != null && !completer.isCompleted) completer.complete();
    this._imageTransform.dispose();
    if (this.widget.stateRef.current === this) this.widget.stateRef.current = null;
    const restore = this._restoreFocus;
    if (restore != null && restore.isConnected) restore.focus({ preventScroll: true });
  }

  // -------------------------------------------------------------------------
  // Gestures.

  _handlePreviewTap(position: _Offset, contentRect: _Rect): void {
    if (this._isAndroidPreview) {
      this._handlePreviewInteraction();
      return;
    }
    if (!this._visibleContentRect(contentRect).contains(position)) unawaited(this._close());
  }

  _handlePreviewScaleStart(localFocalPoint: _Offset): void {
    const transform = this._imageTransform.value;
    this._gestureStartScale = transform.scale;
    this._gestureStartContentFocal = {
      x: (localFocalPoint.x - transform.offset.x) / transform.scale,
      y: (localFocalPoint.y - transform.offset.y) / transform.scale,
    };
  }

  _handlePreviewScaleUpdate(details: { localFocalPoint: _Offset; scale: number }, viewport: _Size, contentRect: _Rect): void {
    const startScale = this._gestureStartScale ?? this._imageTransform.value.scale;
    const contentFocal = this._gestureStartContentFocal ?? this._contentPointFor(details.localFocalPoint);
    const nextScale = _clamp(startScale * details.scale, _ImagePreviewOverlayState._minImageScale, _ImagePreviewOverlayState._maxImageScale);
    const nextTranslation = {
      x: details.localFocalPoint.x - contentFocal.x * nextScale,
      y: details.localFocalPoint.y - contentFocal.y * nextScale,
    };
    this._setImageTransform(nextScale, nextTranslation, viewport, contentRect);
  }

  _handlePreviewScaleEnd(viewport: _Size, contentRect: _Rect): void {
    this._gestureStartScale = null;
    this._gestureStartContentFocal = null;
    this._setImageTransform(this._imageTransform.value.scale, this._currentImageTranslation(), viewport, contentRect);
  }

  _handlePreviewScroll(deltaY: number, viewport: _Size, contentRect: _Rect): void {
    if (deltaY === 0) return;
    const scrollSteps = this._normalizedWheelSteps(deltaY);
    const scaleDelta = Math.pow(_ImagePreviewOverlayState._wheelScaleBase, -scrollSteps);
    const nextScale = _clamp(
      this._imageTransform.value.scale * scaleDelta,
      _ImagePreviewOverlayState._minImageScale,
      _ImagePreviewOverlayState._maxImageScale,
    );
    const imageCenter = this._visibleContentRect(contentRect).center;
    const nextTranslation = {
      x: imageCenter.x - contentRect.center.x * nextScale,
      y: imageCenter.y - contentRect.center.y * nextScale,
    };
    this._setImageTransform(nextScale, nextTranslation, viewport, contentRect);
  }

  _normalizedWheelSteps(deltaY: number): number {
    const magnitude = Math.abs(deltaY);
    if (magnitude === 0) return 0;
    if (magnitude >= 20) return deltaY / 120;
    return Math.sign(deltaY);
  }

  _currentImageTranslation(): _Offset {
    return this._imageTransform.value.offset;
  }

  _contentPointFor(viewportPoint: _Offset): _Offset {
    const transform = this._imageTransform.value;
    return {
      x: (viewportPoint.x - transform.offset.x) / transform.scale,
      y: (viewportPoint.y - transform.offset.y) / transform.scale,
    };
  }

  _visibleContentRect(contentRect: _Rect): _Rect {
    const transform = this._imageTransform.value;
    const scale = transform.scale;
    const offset = transform.offset;
    return new _Rect(
      contentRect.left * scale + offset.x,
      contentRect.top * scale + offset.y,
      contentRect.right * scale + offset.x,
      contentRect.bottom * scale + offset.y,
    );
  }

  _previewContentRect(viewport: _Size, imageSize: _Size): _Rect {
    if (this.widget.forceSquare) {
      const side = this._squarePreviewSide(viewport);
      return _Rect.fromLTWH((viewport.width - side) / 2, (viewport.height - side) / 2, side, side);
    }
    if (imageSize.width <= 0 || imageSize.height <= 0) {
      return _Rect.fromLTWH(0, 0, viewport.width, viewport.height);
    }
    // applyBoxFit(BoxFit.contain) + Alignment.center.inscribe.
    const scale = Math.min(viewport.width / imageSize.width, viewport.height / imageSize.height);
    const width = imageSize.width * scale;
    const height = imageSize.height * scale;
    return _Rect.fromLTWH((viewport.width - width) / 2, (viewport.height - height) / 2, width, height);
  }

  _setImageTransform(scale: number, translation: _Offset, viewport: _Size, contentRect: _Rect): void {
    const clampedScale = _clamp(scale, _ImagePreviewOverlayState._minImageScale, _ImagePreviewOverlayState._maxImageScale);
    const clampedX = this._clampImageTranslationAxis({
      viewportExtent: viewport.width,
      contentStart: contentRect.left,
      contentEnd: contentRect.right,
      scale: clampedScale,
      translation: translation.x,
    });
    const clampedY = this._clampImageTranslationAxis({
      viewportExtent: viewport.height,
      contentStart: contentRect.top,
      contentEnd: contentRect.bottom,
      scale: clampedScale,
      translation: translation.y,
    });
    const next = new _PreviewImageTransform(clampedScale, { x: clampedX, y: clampedY });
    if (this._imageTransform.value.equals(next)) return;
    this._imageTransform.value = next;
  }

  _clampImageTranslationAxis(opts: {
    viewportExtent: number;
    contentStart: number;
    contentEnd: number;
    scale: number;
    translation: number;
  }): number {
    const { viewportExtent, contentStart, contentEnd, scale, translation } = opts;
    const scaledExtent = (contentEnd - contentStart) * scale;
    if (scaledExtent <= viewportExtent) {
      return (viewportExtent - scaledExtent) / 2 - contentStart * scale;
    }
    const minTranslation = viewportExtent - contentEnd * scale;
    const maxTranslation = -contentStart * scale;
    return _clamp(translation, minTranslation, maxTranslation);
  }

  _squarePreviewSide(viewport: _Size): number {
    const availableSide = Math.min(viewport.width, viewport.height);
    return Math.max(160.0, Math.min(360.0, availableSide * 0.72));
  }
}

function _clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function _ImagePreviewOverlay(props: _ImagePreviewOverlayProps) {
  const scopeCache = useMediaCache();
  const resolvedProps = useMemo<_ImagePreviewOverlayProps>(() => ({ ...props, scopeCache }), [props, scopeCache]);
  const state = useViewState<_ImagePreviewOverlayProps, _ImagePreviewOverlayState>(() => new _ImagePreviewOverlayState(), resolvedProps);
  const showControls = !state._closing && (!state._usesImmersiveControls || state._controlsVisible);
  const { showActionBar, frozenBackdrop } = props;

  const fade: CSSProperties = props.animated
    ? {
        opacity: state._routeShown ? 1 : 0,
        transition: `opacity ${state._routeShown ? _routeTransitionDuration : _routeReverseTransitionDuration}ms linear`,
      }
    : {};

  return (
    <div
      ref={(node) => {
        state._rootRef.current = node;
      }}
      tabIndex={-1}
      onMouseMove={state._handlePreviewInteraction}
      onPointerDownCapture={state._handlePreviewInteraction}
      style={{ position: 'absolute', inset: 0, pointerEvents: 'auto', outline: 'none', ...fade }}
    >
      {frozenBackdrop != null ? (
        <Positioned fill>
          <_FrozenPortraitBackdrop snapshot={frozenBackdrop} quarterTurns={state._backdropQuarterTurns} />
        </Positioned>
      ) : null}
      {/* Tapping the backdrop (anywhere outside the image/controls)
          dismisses the overlay. */}
      <Positioned
        fill
        style={{ background: frozenBackdrop != null ? 'transparent' : _previewBarrierColor }}
        onClick={(event) => {
          if (event.button !== 0) return;
          unawaited(state._close());
        }}
      />
      {state._usesImmersiveControls ? (
        <Positioned fill style={{ display: 'flex' }}>
          <_PreviewImageArea state={state} />
        </Positioned>
      ) : (
        <Positioned fill style={{ pointerEvents: 'none' }}>
          <Column crossAxisAlignment="stretch" style={{ height: '100%', padding: '56px 24px 24px', boxSizing: 'border-box' }}>
            <Expanded style={{ pointerEvents: 'auto' }}>
              <_PreviewImageArea state={state} />
            </Expanded>
            {showActionBar ? (
              <>
                <SizedBox height={16} />
                <_PreviewActionBar state={state} />
              </>
            ) : null}
          </Column>
        </Positioned>
      )}
      {state._usesImmersiveControls && showActionBar && showControls ? (
        <Positioned left={0} right={0} bottom={14} style={{ display: 'flex', justifyContent: 'center', pointerEvents: 'none' }}>
          <_PreviewActionBar state={state} />
        </Positioned>
      ) : null}
      {showControls ? (
        <Positioned top={14} right={14}>
          <ButtonIcon
            icon={<Icon icon={Icons.close_rounded} />}
            tooltip="关闭"
            onPressed={() => unawaited(state._close())}
            backgroundColor={_previewControlBackground}
          />
        </Positioned>
      ) : null}
    </div>
  );
}

function _PreviewActionBar({ state }: { state: _ImagePreviewOverlayState }) {
  const busy = state._busy;
  const actions = state.widget.actions;
  const button = (opts: {
    action: _PreviewAction;
    icon: ReactNode;
    tooltip: string;
    task: () => Promise<void>;
    successMessage: string;
  }) => (
    <ButtonIcon
      icon={opts.icon}
      tooltip={opts.tooltip}
      loading={busy === opts.action}
      onPressed={busy == null ? () => unawaited(state._run(opts.action, opts.task, { successMessage: opts.successMessage })) : null}
      backgroundColor={_previewControlBackground}
      style={{ pointerEvents: 'auto' }}
    />
  );
  const { imageUrl, suggestedName } = state.widget;
  return (
    // The row itself is not a hit target (the gaps fall through to the
    // backdrop, like Flutter); only the buttons are.
    <Row mainAxisAlignment="center" mainAxisSize="min" style={{ pointerEvents: 'none', alignSelf: 'center' }}>
      {button({
        action: _PreviewAction.download,
        icon: <Icon icon={Icons.download_rounded} />,
        tooltip: '下载',
        task: () => actions.onDownload(imageUrl, suggestedName),
        successMessage: '已保存到下载文件夹',
      })}
      <SizedBox width={10} />
      {button({
        action: _PreviewAction.saveAs,
        icon: <Icon icon={Icons.save_alt_rounded} />,
        tooltip: '另存为',
        task: () => actions.onSaveAs(imageUrl, suggestedName),
        successMessage: '已保存',
      })}
      <SizedBox width={10} />
      {button({
        action: _PreviewAction.copy,
        icon: <Icon icon={Icons.copy_rounded} />,
        tooltip: '复制到剪贴板',
        task: () => actions.onCopyToClipboard(imageUrl),
        successMessage: '已复制到剪贴板',
      })}
      {state._canSaveSticker ? (
        <>
          <SizedBox width={10} />
          {button({
            action: _PreviewAction.saveSticker,
            icon: <Icon icon={Icons.add_reaction_outlined} />,
            tooltip: '添加到我的表情包',
            task: () => {
              const source = state.widget.stickerSource!;
              return actions.onSaveSticker!(source.message, source.attachment);
            },
            successMessage: '已添加到我的表情包',
          })}
        </>
      ) : null}
      {state._canSaveRoomSticker ? (
        <>
          <SizedBox width={10} />
          {button({
            action: _PreviewAction.saveRoomSticker,
            icon: <Icon icon={Icons.library_add_outlined} />,
            tooltip: '添加到房间表情包',
            task: () => {
              const source = state.widget.stickerSource!;
              return actions.onSaveRoomSticker!(source.message, source.attachment);
            },
            successMessage: '已添加到房间表情包',
          })}
        </>
      ) : null}
    </Row>
  );
}

/** `_buildImage()`: the LayoutBuilder + FutureBuilder around the image. */
function _PreviewImageArea({ state }: { state: _ImagePreviewOverlayState }) {
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  const viewport: _Size = { width: Math.max(1.0, size?.width ?? 1), height: Math.max(1.0, size?.height ?? 1) };
  const data = state._previewImageSnapshot.data;
  return (
    <div ref={ref} style={{ position: 'relative', flex: '1 1 0', minWidth: 0, minHeight: 0, width: '100%', height: '100%' }}>
      {size == null ? null : data == null ? (
        <_PreviewImagePlaceholder error={state._previewImageSnapshot.error} />
      ) : (
        <_PreviewLoadedImage state={state} viewport={viewport} data={data} />
      )}
    </div>
  );
}

function _PreviewImagePlaceholder({ error }: { error: unknown }) {
  return (
    // Opaque and swallowing taps (Dart `onTap: () {}`), so tapping a loading
    // image never closes the preview.
    <div
      data-testid={_ImagePreviewOverlayState._previewImageKey}
      onClick={(event) => event.stopPropagation()}
      style={{ position: 'absolute', inset: 0 }}
    >
      {error == null ? _previewLoading() : _previewError()}
    </div>
  );
}

function _previewLoading(): ReactNode {
  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <CircularProgressIndicator size={28} strokeWidth={2} color={UiColors.textSecondary} />
    </div>
  );
}

function _previewError(): ReactNode {
  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Icon icon={Icons.broken_image_outlined} color={UiColors.textMuted} size={48} />
    </div>
  );
}

interface _TrackedPointer {
  x: number;
  y: number;
  kind: string;
}

/**
 * Pointer bookkeeping of Flutter's `ScaleGestureRecognizer` + `onTapUp` on the
 * same detector: a tap wins when the pointer goes up within the tap slop; a
 * pan / pinch is accepted once it passes the pan or scale slop, and every
 * change in the pointer count ends the gesture and restarts it with a new
 * baseline.
 */
class _ScaleTracker {
  readonly pointers = new Map<number, _TrackedPointer>();
  phase: 'possible' | 'accepted' | 'started' = 'possible';
  initialFocal: _Offset = { x: 0, y: 0 };
  initialSpan = 0;
  tap: { pointerId: number; x: number; y: number } | null = null;

  focal(): _Offset {
    let x = 0;
    let y = 0;
    for (const p of this.pointers.values()) {
      x += p.x;
      y += p.y;
    }
    const count = Math.max(1, this.pointers.size);
    return { x: x / count, y: y / count };
  }

  span(focal: _Offset): number {
    if (this.pointers.size < 2) return 0;
    let total = 0;
    for (const p of this.pointers.values()) total += Math.hypot(p.x - focal.x, p.y - focal.y);
    return total / this.pointers.size;
  }

  rebase(): void {
    this.initialFocal = this.focal();
    this.initialSpan = this.span(this.initialFocal);
  }
}

function _panSlop(kind: string): number {
  // kPrecisePointerPanSlop for mice, kPanSlop for touch.
  return kind === 'mouse' ? 2 : kTouchSlop * 2;
}

function _scaleSlop(kind: string): number {
  // kPrecisePointerScaleSlop for mice, kScaleSlop for touch.
  return kind === 'mouse' ? 1 : kTouchSlop;
}

function _PreviewLoadedImage({ state, viewport, data }: { state: _ImagePreviewOverlayState; viewport: _Size; data: _PreviewImageData }) {
  const contentRect = state._previewContentRect(viewport, data.size);
  const tracker = useRef(new _ScaleTracker()).current;
  const layerRef = useRef<HTMLDivElement | null>(null);
  const latest = useRef({ viewport, contentRect });
  latest.current = { viewport, contentRect };

  // Mouse wheel zoom. Native listener: React's wheel handler is passive and
  // cannot stop Chromium's own ctrl+wheel page zoom.
  useLayoutEffect(() => {
    const node = layerRef.current;
    if (node == null) return;
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const { viewport: v, contentRect: r } = latest.current;
      const deltaY = event.deltaMode === WheelEvent.DOM_DELTA_LINE ? event.deltaY * 40 : event.deltaY;
      // PORT: a trackpad pinch reaches Chromium as ctrl+wheel with small,
      // fractional deltas (Flutter got PointerPanZoom events and scaled
      // around the pointer); a real wheel keeps Dart's stepped zoom.
      if (event.ctrlKey && !Number.isInteger(deltaY)) {
        const rect = node.getBoundingClientRect();
        const focal = { x: event.clientX - rect.left, y: event.clientY - rect.top };
        state._handlePreviewScaleStart(focal);
        state._handlePreviewScaleUpdate({ localFocalPoint: focal, scale: Math.exp(-deltaY / 100) }, v, r);
        state._handlePreviewScaleEnd(v, r);
        return;
      }
      state._handlePreviewScroll(deltaY, v, r);
    };
    node.addEventListener('wheel', onWheel, { passive: false });
    return () => node.removeEventListener('wheel', onWheel);
  }, [state]);

  const localPoint = (event: { clientX: number; clientY: number }): _Offset => {
    const rect = layerRef.current?.getBoundingClientRect();
    return { x: event.clientX - (rect?.left ?? 0), y: event.clientY - (rect?.top ?? 0) };
  };

  const endIfStarted = () => {
    if (tracker.phase === 'started') {
      const { viewport: v, contentRect: r } = latest.current;
      state._handlePreviewScaleEnd(v, r);
      tracker.phase = 'accepted';
    }
  };

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    // Flutter's recognizers only take the primary button.
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    endIfStarted();
    const point = localPoint(event);
    tracker.pointers.set(event.pointerId, { ...point, kind: event.pointerType });
    tracker.tap = tracker.pointers.size === 1 ? { pointerId: event.pointerId, x: point.x, y: point.y } : null;
    tracker.rebase();
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const pointer = tracker.pointers.get(event.pointerId);
    if (pointer == null) return;
    const point = localPoint(event);
    pointer.x = point.x;
    pointer.y = point.y;
    const tap = tracker.tap;
    if (tap != null && tap.pointerId === event.pointerId && Math.hypot(point.x - tap.x, point.y - tap.y) > kTouchSlop) {
      tracker.tap = null;
    }
    const focal = tracker.focal();
    const span = tracker.span(focal);
    const { viewport: v, contentRect: r } = latest.current;
    if (tracker.phase === 'possible') {
      const focalDelta = Math.hypot(focal.x - tracker.initialFocal.x, focal.y - tracker.initialFocal.y);
      const spanDelta = Math.abs(span - tracker.initialSpan);
      if (spanDelta > _scaleSlop(pointer.kind) || focalDelta > _panSlop(pointer.kind)) {
        tracker.tap = null;
        tracker.phase = 'accepted';
        tracker.rebase();
      } else {
        return;
      }
    }
    if (tracker.phase === 'accepted') {
      tracker.phase = 'started';
      tracker.rebase();
      state._handlePreviewScaleStart(tracker.initialFocal);
    }
    const scale = tracker.initialSpan > 0 ? span / tracker.initialSpan : 1;
    state._handlePreviewScaleUpdate({ localFocalPoint: focal, scale }, v, r);
  };

  const onPointerUp = (event: ReactPointerEvent<HTMLDivElement>, cancelled: boolean) => {
    if (!tracker.pointers.has(event.pointerId)) return;
    const point = localPoint(event);
    const tap = tracker.tap;
    const wasTap = !cancelled && tap != null && tap.pointerId === event.pointerId && tracker.phase === 'possible';
    tracker.pointers.delete(event.pointerId);
    tracker.tap = null;
    endIfStarted();
    if (tracker.pointers.size === 0) {
      tracker.phase = 'possible';
    } else {
      tracker.rebase();
    }
    if (wasTap) {
      const { contentRect: r } = latest.current;
      state._handlePreviewTap(point, r);
    }
  };

  return (
    // The gesture layer intentionally fills the whole preview area. If it
    // only covers the painted image, a dragged image can move out from
    // under the pointer and become difficult to grab again.
    <div
      ref={layerRef}
      data-testid="chat-image-preview-viewer"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={(event) => onPointerUp(event, false)}
      onPointerCancel={(event) => onPointerUp(event, true)}
      onClick={(event) => event.stopPropagation()}
      onDragStart={(event) => event.preventDefault()}
      style={{ position: 'absolute', inset: 0, overflow: 'hidden', touchAction: 'none' }}
    >
      <_PreviewTransformLayer state={state}>
        <div style={{ position: 'relative', width: viewport.width, height: viewport.height }}>
          <_PreviewImage state={state} data={data} contentRect={contentRect} />
        </div>
      </_PreviewTransformLayer>
    </div>
  );
}

function _PreviewTransformLayer({ state, children }: { state: _ImagePreviewOverlayState; children: ReactNode }) {
  useListenable(state._imageTransform);
  const transform = state._imageTransform.value;
  return (
    <div
      data-testid="chat-image-preview-image-transform"
      style={{ position: 'absolute', left: 0, top: 0, transform: transform.matrix, transformOrigin: '0 0' }}
    >
      {children}
    </div>
  );
}

function _PreviewImage({ state, data, contentRect }: { state: _ImagePreviewOverlayState; data: _PreviewImageData; contentRect: _Rect }) {
  // Image.errorBuilder: a failed decode shows the broken-image glyph.
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  return (
    <div
      style={{
        position: 'absolute',
        left: contentRect.left,
        top: contentRect.top,
        width: contentRect.width,
        height: contentRect.height,
      }}
    >
      {failedSrc === data.image ? (
        _previewError()
      ) : (
        <img
          data-testid={_ImagePreviewOverlayState._previewImageKey}
          src={data.image}
          alt=""
          draggable={false}
          onError={() => setFailedSrc(data.image)}
          style={{
            display: 'block',
            width: '100%',
            height: '100%',
            objectFit: state.widget.forceSquare ? 'cover' : 'contain',
            userSelect: 'none',
            pointerEvents: 'none',
          }}
        />
      )}
    </div>
  );
}

function _FrozenPortraitBackdrop({ snapshot, quarterTurns }: { snapshot: ImagePreviewBackdropSnapshot; quarterTurns: number }) {
  const normalizedQuarterTurns = ((quarterTurns % 4) + 4) % 4;
  const rotatedSize =
    normalizedQuarterTurns % 2 === 1
      ? { width: snapshot.logicalSize.height, height: snapshot.logicalSize.width }
      : snapshot.logicalSize;
  // PORT: the Dart AnimatedSwitcher cross-fade between rotations is dropped
  // (this branch only runs on Android, which stays on Flutter).
  return (
    <div style={{ position: 'absolute', inset: 0, background: UiColors.black, overflow: 'hidden' }}>
      <div
        data-testid="chat-image-preview-frozen-backdrop-frame"
        style={{
          position: 'absolute',
          left: '50%',
          top: '50%',
          width: rotatedSize.width,
          height: rotatedSize.height,
          transform: 'translate(-50%, -50%)',
        }}
      >
        <img
          data-testid="chat-image-preview-frozen-backdrop"
          src={snapshot.image}
          alt=""
          style={{
            position: 'absolute',
            left: '50%',
            top: '50%',
            width: snapshot.logicalSize.width,
            height: snapshot.logicalSize.height,
            transform: `translate(-50%, -50%) rotate(${normalizedQuarterTurns * 90}deg)`,
            objectFit: 'fill',
          }}
        />
      </div>
      <div style={{ position: 'absolute', inset: 0, background: argb(0xd1000000) }} />
    </div>
  );
}

