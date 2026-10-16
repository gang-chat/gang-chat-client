import { useEffect, useState, type CSSProperties, type ReactNode } from 'react';

import { MediaCacheRequest, type MediaCacheController } from '@gang-chat/core/app/media_cache_controller';
import { hasPlatform, platform } from '@gang-chat/core/platform/platform';

import { CircularProgressIndicator } from '../lib/progress_indicator';
import { Icon, Icons } from './icon';
import { UiColors } from './tokens';

/** Port of Flutter's `BoxFit`, mapped onto CSS `object-fit`. */
export enum BoxFit {
  fill = 'fill',
  contain = 'contain',
  cover = 'cover',
  fitWidth = 'fitWidth',
  fitHeight = 'fitHeight',
  none = 'none',
  scaleDown = 'scaleDown',
}

export function objectFitFor(fit: BoxFit | null | undefined): CSSProperties['objectFit'] {
  switch (fit) {
    case BoxFit.fill:
      return 'fill';
    case BoxFit.contain:
    case BoxFit.fitWidth:
    case BoxFit.fitHeight:
      return 'contain';
    case BoxFit.cover:
      return 'cover';
    case BoxFit.none:
      return 'none';
    case BoxFit.scaleDown:
    case null:
    case undefined:
      return 'scale-down';
  }
}

export type CachedAssetImageErrorBuilder = (error: unknown) => ReactNode;

/** Resolves an http(s) media URL to the one the renderer displays (the host's disk cache). */
function displayUrl(url: string): string {
  return hasPlatform() ? platform().cachedMediaUrl(url) : url;
}

/**
 * Loads [request] as an image source. Port of `cachedMediaImageProvider`: the
 * Dart version read the bytes through [cache]; here the host serves cached
 * media itself (`platform().cachedMediaUrl`), so the result is the URL an
 * `<img>` loads.
 *
 * PORT: [cache] is unused — the desktop host's `gang-media://` protocol owns
 * the on-disk image cache.
 */
export async function cachedMediaImageProvider(_cache: MediaCacheController, request: MediaCacheRequest): Promise<string> {
  return displayUrl(request.uri);
}

export interface CachedAssetImageProps {
  url: string;
  filename?: string | null;
  mimeType?: string | null;
  expectedBytes?: number | null;
  namespace?: string;
  /** Kept for API parity; see [cachedMediaImageProvider]. */
  cache?: MediaCacheController | null;
  width?: number | null;
  height?: number | null;
  fit?: BoxFit | null;
  loadingBuilder?: (() => ReactNode) | null;
  errorBuilder?: CachedAssetImageErrorBuilder | null;
  /** Extra style for the `<img>` (DOM addition). */
  style?: CSSProperties;
  alt?: string;
}

type LoadState = { status: 'loading' } | { status: 'loaded' } | { status: 'error'; error: unknown };

export function CachedAssetImage({
  url,
  filename,
  mimeType,
  expectedBytes,
  namespace = 'asset',
  width,
  height,
  fit,
  loadingBuilder,
  errorBuilder,
  style,
  alt = '',
}: CachedAssetImageProps) {
  const request = MediaCacheRequest.tryFromUrl({ url, filename, mimeType, expectedBytes, namespace });
  const src = request == null ? null : displayUrl(request.uri);
  const [state, setState] = useState<LoadState>(() =>
    src == null ? { status: 'error', error: new Error('图片地址无效') } : { status: 'loading' },
  );

  useEffect(() => {
    if (src == null) {
      setState({ status: 'error', error: new Error('图片地址无效') });
      return;
    }
    // gaplessPlayback: a loaded image stays up while its replacement loads.
    setState((previous) => (previous.status === 'loaded' ? previous : { status: 'loading' }));
  }, [src]);

  const boxWidth = width ?? undefined;
  const boxHeight = height ?? undefined;
  // Without explicit dimensions the image fills the box its parent gives it
  // (avatars, previews), which is how the Flutter call sites size it.
  const imageWidth: CSSProperties['width'] = width ?? (height == null ? '100%' : 'auto');
  const imageHeight: CSSProperties['height'] = height ?? (width == null ? '100%' : 'auto');

  return (
    <>
      {src != null ? (
        <img
          src={src}
          alt={alt}
          draggable={false}
          onLoad={() => setState({ status: 'loaded' })}
          onError={(event) => setState({ status: 'error', error: event })}
          style={{
            display: state.status === 'loaded' ? 'block' : 'none',
            width: imageWidth,
            height: imageHeight,
            objectFit: objectFitFor(fit),
            ...style,
          }}
        />
      ) : null}
      {state.status === 'loading'
        ? (loadingBuilder?.() ?? <_DefaultCachedAssetImageLoading width={boxWidth} height={boxHeight} />)
        : null}
      {state.status === 'error'
        ? (errorBuilder?.(state.error) ?? <_DefaultCachedAssetImageError width={boxWidth} height={boxHeight} />)
        : null}
    </>
  );
}

function _placeholderBox(width: number | undefined, height: number | undefined): CSSProperties {
  return {
    width: width ?? '100%',
    height: height ?? '100%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  };
}

function _DefaultCachedAssetImageLoading({ width, height }: { width?: number; height?: number }) {
  return (
    <div style={_placeholderBox(width, height)}>
      <CircularProgressIndicator size={18} strokeWidth={2} color={UiColors.textSecondary} />
    </div>
  );
}

function _DefaultCachedAssetImageError({ width, height }: { width?: number; height?: number }) {
  return (
    <div style={_placeholderBox(width, height)}>
      <Icon icon={Icons.broken_image_outlined} color={UiColors.textMuted} size={24} />
    </div>
  );
}
