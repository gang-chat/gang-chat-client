import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';

/**
 * Keeps the Android live music-box surface anchored while the IME overlays
 * the room. Other home inputs retain the normal resize behavior.
 *
 * PORT: Dart took a `TargetPlatform`; [platform] is the host OS name
 * (`HostPlatform.operatingSystem`, e.g. `'android'`).
 */
export function shouldResizeHomeForKeyboard(opts: { platform: string; liveMusicBoxVisible: boolean }): boolean {
  return opts.platform !== 'android' || !opts.liveMusicBoxVisible;
}

export interface HomeKeyboardOverlayViewportProps {
  enabled: boolean;
  children?: ReactNode;
}

interface _ViewportSize {
  width: number;
  height: number;
}

/**
 * Whether an on-screen keyboard currently covers part of the window. The DOM
 * stand-in for `MediaQuery.viewInsets.bottom > 0`: the visual viewport
 * shrinks below the layout viewport while a virtual keyboard is shown.
 */
function _keyboardVisible(): boolean {
  if (typeof window === 'undefined') return false;
  const viewport = window.visualViewport;
  if (viewport == null) return false;
  return window.innerHeight - viewport.height > 0.5;
}

/**
 * Preserves the pre-keyboard home geometry while Android animates the IME.
 *
 * Android devices can still deliver progressively shorter root constraints
 * during `adjustResize`, even when the surrounding scaffold opts out of inset
 * resizing. The cached viewport prevents those intermediate constraints from
 * repeatedly re-laying out the live room and music box. The cache is refreshed
 * as soon as the keyboard is fully closed.
 *
 * PORT: the Flutter version also replaced the `MediaQuery` padding of the
 * subtree; DOM layout has no inherited media query, so only the box size is
 * pinned (top-left aligned, overflowing like `OverflowBox`).
 */
export function HomeKeyboardOverlayViewport({ enabled, children }: HomeKeyboardOverlayViewportProps) {
  const ref = useRef<HTMLDivElement | null>(null);
  const stableSize = useRef<_ViewportSize | null>(null);
  const [pinned, setPinned] = useState<_ViewportSize | null>(null);

  useLayoutEffect(() => {
    if (!enabled) {
      stableSize.current = null;
      setPinned(null);
      return;
    }
    const node = ref.current;
    if (node == null) return;
    const update = () => {
      const keyboardVisible = _keyboardVisible();
      const current = { width: node.clientWidth, height: node.clientHeight };
      if (!keyboardVisible) stableSize.current = current;
      const stable = stableSize.current;
      const next = keyboardVisible && stable != null ? stable : null;
      setPinned((previous) =>
        previous === next ||
        (previous != null && next != null && previous.width === next.width && previous.height === next.height)
          ? previous
          : next,
      );
    };
    update();
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(update);
    observer?.observe(node);
    window.visualViewport?.addEventListener('resize', update);
    return () => {
      observer?.disconnect();
      window.visualViewport?.removeEventListener('resize', update);
    };
  }, [enabled]);

  if (!enabled) return <>{children}</>;
  return (
    <div ref={ref} style={{ position: 'relative', flex: '1 1 0', alignSelf: 'stretch', minWidth: 0, minHeight: 0 }}>
      <div
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          width: pinned?.width ?? '100%',
          height: pinned?.height ?? '100%',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {children}
      </div>
    </div>
  );
}
