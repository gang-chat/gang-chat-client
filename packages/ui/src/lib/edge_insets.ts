import type { CSSProperties } from 'react';

/**
 * Port of Flutter's `EdgeInsets` / `EdgeInsetsGeometry` for component props
 * such as `PressableSurface.padding`. Components accept either a number (all
 * sides) or an insets object; build them with the Dart-named helpers:
 *
 * ```ts
 * EdgeInsets.symmetric({ horizontal: 16 })   // EdgeInsets.symmetric(horizontal: 16)
 * EdgeInsets.fromLTRB(13, 11, 13, 11)
 * EdgeInsets.only({ left: 12, top: 12 })
 * ```
 */
export interface EdgeInsetsSpec {
  readonly left?: number;
  readonly top?: number;
  readonly right?: number;
  readonly bottom?: number;
  readonly horizontal?: number;
  readonly vertical?: number;
}

export type EdgeInsetsValue = number | EdgeInsetsSpec;

export interface ResolvedEdgeInsets {
  readonly left: number;
  readonly top: number;
  readonly right: number;
  readonly bottom: number;
}

export const EdgeInsets = {
  zero: { left: 0, top: 0, right: 0, bottom: 0 } as ResolvedEdgeInsets,
  all(value: number): ResolvedEdgeInsets {
    return { left: value, top: value, right: value, bottom: value };
  },
  symmetric(opts: { horizontal?: number; vertical?: number } = {}): ResolvedEdgeInsets {
    const { horizontal = 0, vertical = 0 } = opts;
    return { left: horizontal, top: vertical, right: horizontal, bottom: vertical };
  },
  only(opts: { left?: number; top?: number; right?: number; bottom?: number } = {}): ResolvedEdgeInsets {
    const { left = 0, top = 0, right = 0, bottom = 0 } = opts;
    return { left, top, right, bottom };
  },
  fromLTRB(left: number, top: number, right: number, bottom: number): ResolvedEdgeInsets {
    return { left, top, right, bottom };
  },
};

export function resolveEdgeInsets(value: EdgeInsetsValue | null | undefined): ResolvedEdgeInsets {
  if (value == null) return EdgeInsets.zero;
  if (typeof value === 'number') return EdgeInsets.all(value);
  return {
    left: value.left ?? value.horizontal ?? 0,
    top: value.top ?? value.vertical ?? 0,
    right: value.right ?? value.horizontal ?? 0,
    bottom: value.bottom ?? value.vertical ?? 0,
  };
}

export function horizontalInsets(value: EdgeInsetsValue | null | undefined): number {
  const r = resolveEdgeInsets(value);
  return r.left + r.right;
}

export function verticalInsets(value: EdgeInsetsValue | null | undefined): number {
  const r = resolveEdgeInsets(value);
  return r.top + r.bottom;
}

export function paddingStyle(value: EdgeInsetsValue | null | undefined): CSSProperties {
  const r = resolveEdgeInsets(value);
  return { paddingLeft: r.left, paddingTop: r.top, paddingRight: r.right, paddingBottom: r.bottom };
}

export function marginStyle(value: EdgeInsetsValue | null | undefined): CSSProperties {
  const r = resolveEdgeInsets(value);
  return { marginLeft: r.left, marginTop: r.top, marginRight: r.right, marginBottom: r.bottom };
}
