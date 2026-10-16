import { forwardRef, type CSSProperties, type HTMLAttributes, type ReactNode } from 'react';

/**
 * Flutter-shaped layout helpers over CSS flexbox.
 *
 * They exist so ported widget trees read like their Dart originals and pick up
 * Flutter's defaults (notably `crossAxisAlignment: center`, where CSS would
 * stretch). They are thin: everything else is ordinary `style`.
 *
 *  - `Row` / `Column`: `mainAxisAlignment`, `crossAxisAlignment`,
 *    `mainAxisSize` ('max' fills the main axis like Flutter, 'min' shrink-wraps),
 *    `gap` (Flutter `spacing`).
 *  - `Expanded` (`flex: n 1 0`) / `Flexible` (`flex: 0 1 auto`): both set the
 *    min size to 0 so long text ellipsizes instead of overflowing.
 *  - `SizedBox`, `Spacer`, `Center`, `Stack` + `Positioned`.
 *  - `Text`: `maxLines` + ellipsis / clamp, `selectable`.
 */

export type MainAxisAlignment = 'start' | 'end' | 'center' | 'spaceBetween' | 'spaceAround' | 'spaceEvenly';
export type CrossAxisAlignment = 'start' | 'end' | 'center' | 'stretch' | 'baseline';
export type MainAxisSize = 'max' | 'min';

const justify: Record<MainAxisAlignment, CSSProperties['justifyContent']> = {
  start: 'flex-start',
  end: 'flex-end',
  center: 'center',
  spaceBetween: 'space-between',
  spaceAround: 'space-around',
  spaceEvenly: 'space-evenly',
};

const align: Record<CrossAxisAlignment, CSSProperties['alignItems']> = {
  start: 'flex-start',
  end: 'flex-end',
  center: 'center',
  stretch: 'stretch',
  baseline: 'baseline',
};

type DivProps = Omit<HTMLAttributes<HTMLDivElement>, 'style'> & { style?: CSSProperties };

export interface FlexProps extends DivProps {
  mainAxisAlignment?: MainAxisAlignment;
  crossAxisAlignment?: CrossAxisAlignment;
  mainAxisSize?: MainAxisSize;
  gap?: number;
  children?: ReactNode;
}

function flexStyle(direction: 'row' | 'column', props: FlexProps): CSSProperties {
  const { mainAxisAlignment = 'start', crossAxisAlignment = 'center', mainAxisSize = 'max', gap } = props;
  const style: CSSProperties = {
    display: 'flex',
    flexDirection: direction,
    justifyContent: justify[mainAxisAlignment],
    alignItems: align[crossAxisAlignment],
    boxSizing: 'border-box',
    minWidth: 0,
    minHeight: 0,
  };
  if (gap != null) style.gap = gap;
  if (mainAxisSize === 'min') {
    if (direction === 'row') style.width = 'fit-content';
    else style.height = 'fit-content';
  }
  return style;
}

function splitFlexProps(props: FlexProps): DivProps {
  const { mainAxisAlignment: _a, crossAxisAlignment: _b, mainAxisSize: _c, gap: _d, ...rest } = props;
  return rest;
}

export const Row = forwardRef<HTMLDivElement, FlexProps>(function Row(props, ref) {
  const { style, children, ...rest } = splitFlexProps(props) as FlexProps;
  return (
    <div ref={ref} {...rest} style={{ ...flexStyle('row', props), ...style }}>
      {children}
    </div>
  );
});

export const Column = forwardRef<HTMLDivElement, FlexProps>(function Column(props, ref) {
  const { style, children, ...rest } = splitFlexProps(props) as FlexProps;
  return (
    <div ref={ref} {...rest} style={{ ...flexStyle('column', props), ...style }}>
      {children}
    </div>
  );
});

export interface ExpandedProps extends DivProps {
  flex?: number;
  children?: ReactNode;
}

/** Port of `Expanded`: takes the remaining main-axis space. */
export const Expanded = forwardRef<HTMLDivElement, ExpandedProps>(function Expanded({ flex = 1, style, children, ...rest }, ref) {
  return (
    <div ref={ref} {...rest} style={{ flex: `${flex} 1 0`, minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column', ...style }}>
      {children}
    </div>
  );
});

/** Port of `Flexible(fit: FlexFit.loose)`: may shrink below its content size. */
export const Flexible = forwardRef<HTMLDivElement, ExpandedProps>(function Flexible({ flex = 1, style, children, ...rest }, ref) {
  return (
    <div ref={ref} {...rest} style={{ flex: `0 ${flex} auto`, minWidth: 0, minHeight: 0, ...style }}>
      {children}
    </div>
  );
});

export interface SizedBoxProps extends DivProps {
  width?: number | string;
  height?: number | string;
  children?: ReactNode;
}

/** Port of `SizedBox`: a fixed-size box that does not shrink in flex layouts. */
export const SizedBox = forwardRef<HTMLDivElement, SizedBoxProps>(function SizedBox({ width, height, style, children, ...rest }, ref) {
  return (
    <div ref={ref} {...rest} style={{ width, height, flexShrink: 0, boxSizing: 'border-box', ...style }}>
      {children}
    </div>
  );
});

/** Port of `Spacer`. */
export function Spacer({ flex = 1 }: { flex?: number }) {
  return <div style={{ flex: `${flex} 1 0`, minWidth: 0, minHeight: 0 }} />;
}

/** Port of `Center`. */
export const Center = forwardRef<HTMLDivElement, DivProps & { children?: ReactNode }>(function Center({ style, children, ...rest }, ref) {
  return (
    <div ref={ref} {...rest} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', ...style }}>
      {children}
    </div>
  );
});

/** Port of `Stack`: children are positioned against this box. */
export const Stack = forwardRef<HTMLDivElement, DivProps & { children?: ReactNode; clip?: boolean }>(function Stack(
  { style, children, clip = false, ...rest },
  ref,
) {
  return (
    <div ref={ref} {...rest} style={{ position: 'relative', overflow: clip ? 'hidden' : undefined, ...style }}>
      {children}
    </div>
  );
});

export interface PositionedProps extends DivProps {
  top?: number | string;
  left?: number | string;
  right?: number | string;
  bottom?: number | string;
  width?: number | string;
  height?: number | string;
  /** `Positioned.fill`. */
  fill?: boolean;
  children?: ReactNode;
}

/** Port of `Positioned` / `Positioned.fill`. */
export const Positioned = forwardRef<HTMLDivElement, PositionedProps>(function Positioned(
  { top, left, right, bottom, width, height, fill = false, style, children, ...rest },
  ref,
) {
  const inset: CSSProperties = fill ? { top: top ?? 0, left: left ?? 0, right: right ?? 0, bottom: bottom ?? 0 } : { top, left, right, bottom };
  return (
    <div ref={ref} {...rest} style={{ position: 'absolute', ...inset, width, height, ...style }}>
      {children}
    </div>
  );
});

export interface TextProps extends Omit<HTMLAttributes<HTMLSpanElement>, 'style'> {
  style?: CSSProperties;
  /** Flutter `maxLines`: 1 → single line with ellipsis, n → clamp to n lines. */
  maxLines?: number;
  /** Flutter `overflow`; defaults to ellipsis when [maxLines] is set. */
  overflow?: 'ellipsis' | 'clip' | 'visible';
  /** Whether the text can be selected (Flutter `SelectableText` / `SelectionArea`). */
  selectable?: boolean;
  textAlign?: CSSProperties['textAlign'];
  /** Flutter `softWrap: false`. */
  softWrap?: boolean;
  children?: ReactNode;
}

/** Port of Flutter's `Text`. Renders a block-level span by default. */
export const Text = forwardRef<HTMLSpanElement, TextProps>(function Text(
  { style, maxLines, overflow, selectable = false, textAlign, softWrap = true, children, ...rest },
  ref,
) {
  const resolvedOverflow = overflow ?? (maxLines != null ? 'ellipsis' : 'visible');
  const clampStyle: CSSProperties = {};
  if (maxLines === 1 || !softWrap) {
    clampStyle.whiteSpace = 'nowrap';
    clampStyle.overflow = resolvedOverflow === 'visible' ? 'visible' : 'hidden';
    if (resolvedOverflow === 'ellipsis') clampStyle.textOverflow = 'ellipsis';
  } else if (maxLines != null && maxLines > 1) {
    clampStyle.display = '-webkit-box';
    clampStyle.WebkitBoxOrient = 'vertical';
    clampStyle.WebkitLineClamp = maxLines;
    clampStyle.overflow = 'hidden';
    clampStyle.whiteSpace = 'pre-wrap';
  } else {
    clampStyle.whiteSpace = 'pre-wrap';
  }
  return (
    <span
      ref={ref}
      {...rest}
      style={{
        display: 'block',
        minWidth: 0,
        overflowWrap: 'break-word',
        textAlign,
        userSelect: selectable ? 'text' : 'none',
        WebkitUserSelect: selectable ? 'text' : 'none',
        cursor: selectable ? 'text' : undefined,
        ...clampStyle,
        ...style,
      }}
    >
      {children}
    </span>
  );
});
