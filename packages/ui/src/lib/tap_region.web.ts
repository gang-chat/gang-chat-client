import { useEffect, useRef, type RefObject } from 'react';
import type { View } from 'react-native';

/**
 * Port of Flutter's `TapRegion.onTapOutside` for the web: a pointer down
 * anywhere outside the [regions] (one tap-region group) calls [onTapOutside].
 * The pointer event still reaches whatever it landed on, like Flutter.
 *
 * react-native-web View refs are DOM elements, so containment is a DOM check.
 */
export function useTapOutside(
  regions: ReadonlyArray<RefObject<View | null>>,
  onTapOutside: (event: { x: number; y: number }) => void,
  enabled = true,
): void {
  const callback = useRef(onTapOutside);
  callback.current = onTapOutside;
  const regionsRef = useRef(regions);
  regionsRef.current = regions;

  useEffect(() => {
    if (!enabled || typeof document === 'undefined') return;
    const listener = (event: PointerEvent) => {
      const target = event.target as Node | null;
      if (target == null) return;
      for (const region of regionsRef.current) {
        const node = region.current as unknown as Node | null;
        if (node != null && typeof node.contains === 'function' && node.contains(target)) return;
      }
      callback.current({ x: event.clientX, y: event.clientY });
    };
    document.addEventListener('pointerdown', listener, true);
    return () => document.removeEventListener('pointerdown', listener, true);
  }, [enabled]);
}

/**
 * Whether callers must put a dismiss barrier behind their popup because
 * [useTapOutside] cannot observe outside taps on this platform.
 */
export const kTapOutsideNeedsBarrier = false;
