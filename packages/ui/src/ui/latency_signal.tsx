import { useEffect, useRef, useState } from 'react';

import { useTapOutside } from '../lib/tap_region';
import { UiTooltip, type UiTooltipState } from './tooltip';
import { argb } from './tokens';

export interface LatencySignalBadgeProps {
  activeBars: number;
  activeColor: string;
  tooltip: string;
  size?: number;
}

const _hoverWaitDuration = 350;
const _pinnedExitDuration = 365 * 24 * 60 * 60 * 1000;

export function LatencySignalBadge({ activeBars, activeColor, tooltip, size = 18 }: LatencySignalBadgeProps) {
  const tooltipRef = useRef<UiTooltipState | null>(null);
  const regionRef = useRef<HTMLDivElement | null>(null);
  const [pinned, setPinned] = useState(false);
  const pinnedRef = useRef(pinned);
  pinnedRef.current = pinned;

  const togglePinned = () => {
    if (pinnedRef.current) {
      setPinned(false);
      UiTooltip.dismissAllToolTips();
      return;
    }
    setPinned(true);
  };

  // Post-frame: show the pinned tooltip once the pinned exit duration applies.
  useEffect(() => {
    if (!pinned) return;
    const frame = requestAnimationFrame(() => {
      if (pinnedRef.current) tooltipRef.current?.ensureTooltipVisible();
    });
    return () => cancelAnimationFrame(frame);
  }, [pinned]);

  useTapOutside(
    [regionRef],
    () => {
      if (!pinnedRef.current) return;
      setPinned(false);
      UiTooltip.dismissAllToolTips();
    },
    pinned,
  );

  const bars = Math.min(Math.max(activeBars, 0), 3);
  return (
    <div ref={regionRef} onClick={togglePinned} style={{ display: 'contents' }}>
      <UiTooltip
        ref={tooltipRef}
        message={tooltip}
        waitDuration={_hoverWaitDuration}
        triggerMode="manual"
        // Material Tooltip has no persistent-hover flag. A pinned tooltip
        // therefore keeps its hover-exit timer dormant; any outside pointer
        // down still dismisses it immediately through the normal route.
        exitDuration={pinned ? _pinnedExitDuration : null}
      >
        <div
          data-testid="latency-signal-badge"
          style={{
            width: size,
            height: size - 2,
            flexShrink: 0,
            boxSizing: 'border-box',
            padding: '3px 3.5px',
            display: 'flex',
            flexDirection: 'row',
            justifyContent: 'center',
            alignItems: 'flex-end',
          }}
        >
          {[1, 2, 3].map((index) => (
            <div key={index} style={{ display: 'flex', alignItems: 'flex-end' }}>
              <_SignalBar index={index} active={bars >= index} color={activeColor} />
              {index !== 3 ? <div style={{ width: 1 }} /> : null}
            </div>
          ))}
        </div>
      </UiTooltip>
    </div>
  );
}

function _SignalBar({ index, active, color }: { index: number; active: boolean; color: string }) {
  const fill = active ? color : argb(0xff8a93a3);
  return (
    <div
      data-testid={`latency-signal-bar-${index}`}
      style={{ width: 3, height: 3 + index * 2, backgroundColor: fill, borderRadius: 999, flexShrink: 0 }}
    />
  );
}
