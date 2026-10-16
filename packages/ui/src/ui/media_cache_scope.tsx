import { createContext, useContext, type ReactNode } from 'react';

import { MediaCacheController } from '@gang-chat/core/app/media_cache_controller';

let _fallbackMediaCacheController: MediaCacheController | null = null;

function fallbackMediaCacheController(): MediaCacheController {
  _fallbackMediaCacheController ??= new MediaCacheController();
  return _fallbackMediaCacheController;
}

const MediaCacheContext = createContext<MediaCacheController | null>(null);

export function MediaCacheScope({ cache, children }: { cache: MediaCacheController; children?: ReactNode }) {
  return <MediaCacheContext.Provider value={cache}>{children}</MediaCacheContext.Provider>;
}

/** Port of `MediaCacheScope.of(context)` (a hook): the scope's cache or a shared fallback. */
export function useMediaCache(): MediaCacheController {
  return useContext(MediaCacheContext) ?? fallbackMediaCacheController();
}

/** Port of `MediaCacheScope.maybeOf(context)` (a hook). */
export function useMaybeMediaCache(): MediaCacheController | null {
  return useContext(MediaCacheContext);
}

MediaCacheScope.of = useMediaCache;
MediaCacheScope.maybeOf = useMaybeMediaCache;
