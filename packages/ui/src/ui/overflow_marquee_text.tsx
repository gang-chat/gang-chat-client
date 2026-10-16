import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react';

export interface OverflowMarqueeTextProps {
  text: string;
  style: CSSProperties;
  /** Optional test id for the moving track (Dart `trackKey`). */
  trackKey?: string | null;
}

const _pauseDuration = 850;

/**
 * Keeps a single-line label static while it fits, then moves it between both
 * overflow boundaries with a short pause at either end.
 *
 * This is shared by compact identity and media controls so they use the same
 * overflow threshold, speed, and pause timing instead of falling back to
 * ellipses or maintaining separate marquee implementations.
 */
export function OverflowMarqueeText({ text, style, trackKey }: OverflowMarqueeTextProps) {
  const styleKey = JSON.stringify(style);
  const viewportRef = useRef<HTMLDivElement | null>(null);
  const textRef = useRef<HTMLSpanElement | null>(null);
  const [scrollDistance, setScrollDistance] = useState(0);

  // Measure with the rendered (inherited + [style]) font, like the Dart code
  // that resolved DefaultTextStyle before laying out its TextPainter.
  useLayoutEffect(() => {
    const viewport = viewportRef.current;
    const label = textRef.current;
    if (viewport == null || label == null) return;
    const measure = () => {
      const viewportWidth = viewport.clientWidth;
      // Align the text box to a physical-pixel boundary. Fractional glyph
      // advances otherwise risk clipping the final antialiased column even
      // when the logical widths are mathematically equal.
      const ratio = window.devicePixelRatio || 1;
      const raw = label.getBoundingClientRect().width;
      const textWidth = Math.ceil(raw * ratio) / ratio;
      const distance = Math.max(textWidth - viewportWidth, 0);
      setScrollDistance((previous) => (Math.abs(previous - distance) < 0.1 ? previous : distance));
    };
    measure();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(measure);
    observer.observe(viewport);
    observer.observe(label);
    return () => observer.disconnect();
  }, [text, styleKey]);

  // forward → pause → reverse → pause …, restarted whenever the travel changes.
  useEffect(() => {
    const label = textRef.current;
    if (label == null || scrollDistance <= 0 || typeof label.animate !== 'function') return;
    const duration = Math.round(Math.min(Math.max((scrollDistance / 28) * 1000, 1200), 10000));
    let timer: ReturnType<typeof setTimeout> | null = null;
    let animation: Animation | null = null;
    let cancelled = false;
    const run = (forward: boolean) => {
      timer = setTimeout(() => {
        if (cancelled) return;
        const from = forward ? 0 : -scrollDistance;
        const to = forward ? -scrollDistance : 0;
        animation = label.animate([{ transform: `translateX(${from}px)` }, { transform: `translateX(${to}px)` }], {
          duration,
          easing: 'linear',
          fill: 'forwards',
        });
        animation.onfinish = () => {
          if (!cancelled) run(!forward);
        };
      }, _pauseDuration);
    };
    run(true);
    return () => {
      cancelled = true;
      if (timer != null) clearTimeout(timer);
      animation?.cancel();
    };
  }, [scrollDistance, text]);

  return (
    <div ref={viewportRef} style={{ overflow: 'hidden', minWidth: 0, width: '100%', whiteSpace: 'nowrap', ...style }}>
      <span
        ref={textRef}
        data-testid={trackKey ?? undefined}
        style={{ display: 'inline-block', whiteSpace: 'nowrap', willChange: scrollDistance > 0 ? 'transform' : undefined }}
      >
        {text}
      </span>
    </div>
  );
}
