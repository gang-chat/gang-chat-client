import type { RefObject } from 'react';
import type { View } from 'react-native';

/**
 * Port of Flutter's `TapRegion.onTapOutside`.
 *
 * PORT: React Native has no global pointer stream that lets a view observe
 * taps elsewhere without consuming them. On native this hook does nothing and
 * [kTapOutsideNeedsBarrier] tells popups to put a transparent dismiss barrier
 * behind themselves instead (so the outside tap closes the popup but is not
 * delivered to the control underneath).
 */
export function useTapOutside(
  _regions: ReadonlyArray<RefObject<View | null>>,
  _onTapOutside: (event: { x: number; y: number }) => void,
  _enabled = true,
): void {}

export const kTapOutsideNeedsBarrier = true;
