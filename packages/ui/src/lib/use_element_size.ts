import { useLayoutEffect, useState, type RefObject } from 'react';

export interface ElementSize {
  width: number;
  height: number;
}

/**
 * `LayoutBuilder` stand-in: the element's content-box size, tracked with a
 * ResizeObserver. `null` until the first measurement (before the first paint
 * when the element is already in the DOM).
 */
export function useElementSize(ref: RefObject<HTMLElement | null>): ElementSize | null {
  const [size, setSize] = useState<ElementSize | null>(null);
  useLayoutEffect(() => {
    const node = ref.current;
    if (node == null) return;
    const update = () => {
      const width = node.clientWidth;
      const height = node.clientHeight;
      setSize((previous) =>
        previous != null && Math.abs(previous.width - width) < 0.5 && Math.abs(previous.height - height) < 0.5
          ? previous
          : { width, height },
      );
    };
    update();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(update);
    observer.observe(node);
    return () => observer.disconnect();
  }, [ref]);
  return size;
}
