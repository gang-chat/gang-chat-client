import { useEffect, useRef, type RefObject } from 'react';

/**
 * Port of Flutter's `TapRegion.onTapOutside`: a pointer down anywhere outside
 * every element in [regions] (one tap-region group) calls [onTapOutside]. The
 * pointer event still reaches whatever it landed on, like Flutter.
 */
export function useTapOutside(
  regions: ReadonlyArray<RefObject<HTMLElement | null>>,
  onTapOutside: (event: PointerEvent) => void,
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
        const node = region.current;
        if (node != null && node.contains(target)) return;
      }
      callback.current(event);
    };
    document.addEventListener('pointerdown', listener, true);
    return () => document.removeEventListener('pointerdown', listener, true);
  }, [enabled]);
}
