import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { AppOrientationController } from '@gang-chat/core/shell/app_orientation_controller';

export interface FullScreenMediaOrientationProps {
  children: ReactNode;
  controller?: AppOrientationController;
}

async function ignoreFailure(operation: Promise<void>): Promise<void> {
  try {
    await operation;
  } catch {
    // Orientation support is best-effort and must never break navigation.
  }
}

/**
 * Temporarily enables Android landscape rotation for full-screen media.
 *
 * Unmounting the scope restores the device default: portrait-only on Android
 * phones and unrestricted rotation on Android tablets.
 *
 * PORT: on desktop the controller ignores every request (as in Flutter), so
 * this is a pass-through until a React Native Android host injects a real
 * orientation setter into [AppOrientationController].
 */
export function FullScreenMediaOrientation({ children, controller }: FullScreenMediaOrientationProps) {
  const [fallback] = useState(() => new AppOrientationController());
  const active = controller ?? fallback;

  useEffect(() => {
    void ignoreFailure(active.allowFullScreenMediaLandscape());
    return () => {
      void ignoreFailure(active.restoreDefaultOrientation());
    };
  }, [active]);

  return <>{children}</>;
}
