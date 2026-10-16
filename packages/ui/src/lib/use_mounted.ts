import { useEffect, useRef } from 'react';

/** Returns a ref that is true while the component is mounted (Dart `mounted`). */
export function useMountedRef(): { readonly current: boolean } {
  const ref = useRef(false);
  useEffect(() => {
    ref.current = true;
    return () => {
      ref.current = false;
    };
  }, []);
  return ref;
}
