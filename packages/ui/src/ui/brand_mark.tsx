import { UiColors, UiShadows, boxShadow } from './tokens';

export interface BrandMarkProps {
  size?: number;
  /**
   * Draws the mark on a rounded tile. Without the tile only the strokes are
   * painted, for places that already provide a background.
   */
  tile?: boolean;
}

const _ink = UiColors.onAccent;

/**
 * The Gang Chat mark (headset wrapped around a speech bubble with a voice
 * waveform), drawn as vectors so it always follows the current palette.
 */
export function BrandMark({ size = 28, tile = true }: BrandMarkProps) {
  if (!tile) {
    return (
      <div style={{ width: size, height: size, flexShrink: 0 }}>
        <_BrandMarkSvg ink={UiColors.accent} />
      </div>
    );
  }
  return (
    <div
      style={{
        width: size,
        height: size,
        flexShrink: 0,
        boxSizing: 'border-box',
        padding: size * 0.12,
        borderRadius: size * 0.3,
        backgroundColor: UiColors.accentStrong,
        boxShadow: boxShadow(UiShadows.glow(UiColors.accent, 0.35)),
      }}
    >
      <_BrandMarkSvg ink={_ink} />
    </div>
  );
}

// Geometry on a 64-unit grid. Every element keeps at least half a stroke
// of clear space from its neighbours so nothing overlaps at any size, and
// the mark's ink bounds are centred on the grid.
const _strokeWidth = 4.4;
const _cupTop = 30.5;
const _cupWidth = 7.2;
const _cupHeight = 18.0;
const _leftCupX = 4.0;
const _rightCupX = 52.8;

function _BrandMarkSvg({ ink }: { ink: string }) {
  // Headband: lands exactly on the centre of each cup's top edge, so its
  // caps merge into the cup outline instead of poking inside it.
  const leftCupCenter = _leftCupX + _cupWidth / 2;
  const rightCupCenter = _rightCupX + _cupWidth / 2;
  const headband = `M ${leftCupCenter} ${_cupTop} A ${(rightCupCenter - leftCupCenter) / 2} 22 0 0 1 ${rightCupCenter} ${_cupTop}`;
  // Speech bubble and tail as one outline: the bottom edge stops where the
  // tail opens, so no line runs through it.
  const bubble =
    'M 26 22.5 L 38 22.5 A 8 8 0 0 1 46 30.5 L 46 39.5 A 8 8 0 0 1 38 47.5 L 34 47.5 L 26 55.5 ' +
    'L 26 47.5 A 8 8 0 0 1 18 39.5 L 18 30.5 A 8 8 0 0 1 26 22.5 Z';
  return (
    <svg
      viewBox="0 0 64 64"
      width="100%"
      height="100%"
      aria-hidden="true"
      style={{ display: 'block', overflow: 'visible' }}
      fill="none"
      stroke={ink}
      strokeWidth={_strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={headband} />
      {[_leftCupX, _rightCupX].map((left) => (
        <rect key={left} x={left} y={_cupTop} width={_cupWidth} height={_cupHeight} rx={_cupWidth / 2} ry={_cupWidth / 2} />
      ))}
      <path d={bubble} />
      {/* Voice waveform, centred in the bubble. */}
      <line x1={25} y1={32} x2={25} y2={38} />
      <line x1={32} y1={29} x2={32} y2={41} />
      <line x1={39} y1={32} x2={39} y2={38} />
    </svg>
  );
}
