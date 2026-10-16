import { createContext, useContext, useSyncExternalStore, type ReactNode } from 'react';

import { narrowBreakpoint } from './navigation';

const HomeAdaptiveLayoutContext = createContext<boolean | null>(null);

function _subscribeWindowSize(onChange: () => void): () => void {
  if (typeof window === 'undefined') return () => {};
  window.addEventListener('resize', onChange);
  return () => window.removeEventListener('resize', onChange);
}

function _windowWidth(): number {
  return typeof window === 'undefined' ? 0 : window.innerWidth;
}

/** `MediaQuery.sizeOf(context).width` as a hook. */
function _useWindowWidth(): number {
  return useSyncExternalStore(_subscribeWindowSize, _windowWidth, _windowWidth);
}

/**
 * Port of `HomeAdaptiveLayout.usesCompactLayout(context)` (a hook): the
 * nearest [HomeAdaptiveLayout]'s decision, or the window width when the
 * subtree is not inside one.
 */
function usesCompactLayout(): boolean {
  const mode = useContext(HomeAdaptiveLayoutContext);
  const windowWidth = _useWindowWidth();
  return mode ?? windowWidth < narrowBreakpoint;
}

export interface HomeAdaptiveLayoutProps {
  compact: boolean;
  children?: ReactNode;
}

function HomeAdaptiveLayoutImpl({ compact, children }: HomeAdaptiveLayoutProps) {
  return <HomeAdaptiveLayoutContext.Provider value={compact}>{children}</HomeAdaptiveLayoutContext.Provider>;
}

/**
 * Shares one width-driven compact/wide decision across the home subtree.
 * Platform-specific controls remain responsible for their own OS behavior.
 */
export const HomeAdaptiveLayout = Object.assign(HomeAdaptiveLayoutImpl, { usesCompactLayout });

/** Hook alias of [HomeAdaptiveLayout.usesCompactLayout]. */
export const useHomeCompactLayout = usesCompactLayout;
