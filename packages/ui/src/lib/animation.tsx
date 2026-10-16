import { useEffect, useRef, type ReactNode } from 'react';
import {
  Animated,
  Easing,
  Platform,
  StyleSheet,
  View,
  type StyleProp,
  type ViewProps,
  type ViewStyle,
} from 'react-native';

import { webStyle } from './web_style';

/**
 * Animation helpers standing in for Flutter's implicit animations
 * (`AnimatedContainer`, `AnimatedPositioned`) and `Curves`.
 */

export interface Curve {
  /** CSS timing function (web transitions). */
  readonly css: string;
  /** Easing function (React Native `Animated`). */
  readonly easing: (t: number) => number;
}

function cubic(a: number, b: number, c: number, d: number): Curve {
  return { css: `cubic-bezier(${a}, ${b}, ${c}, ${d})`, easing: Easing.bezier(a, b, c, d) };
}

/** Port of Flutter's `Curves`. */
export const Curves = {
  linear: { css: 'linear', easing: Easing.linear } as Curve,
  ease: cubic(0.25, 0.1, 0.25, 1),
  easeIn: cubic(0.42, 0, 1, 1),
  easeOut: cubic(0, 0, 0.58, 1),
  easeInOut: cubic(0.42, 0, 0.58, 1),
  easeOutCubic: cubic(0.215, 0.61, 0.355, 1),
  easeInCubic: cubic(0.55, 0.055, 0.675, 0.19),
  easeInOutCubic: cubic(0.645, 0.045, 0.355, 1),
  fastOutSlowIn: cubic(0.4, 0, 0.2, 1),
  decelerate: cubic(0, 0, 0.2, 1),
};

const isWeb = Platform.OS === 'web';

/**
 * Port of an implicitly animated number (`AnimatedPositioned.left`,
 * `AnimatedContainer.width`): returns an `Animated.Value` that tweens to
 * [target] whenever it changes. The first render starts at [target].
 */
export function useTweenedValue(target: number, durationMs: number, curve: Curve = Curves.linear): Animated.Value {
  const value = useRef<Animated.Value | null>(null);
  if (value.current == null) value.current = new Animated.Value(target);
  const last = useRef(target);
  useEffect(() => {
    if (last.current === target) return;
    last.current = target;
    const animation = Animated.timing(value.current!, {
      toValue: target,
      duration: durationMs,
      easing: curve.easing,
      useNativeDriver: false,
    });
    animation.start();
    return () => animation.stop();
  }, [target, durationMs, curve]);
  return value.current;
}

/**
 * Tweens between colours on native. Returns the plain colour while idle and
 * an interpolation while a change animates.
 */
function useTweenedColor(
  color: string | undefined,
  durationMs: number,
  curve: Curve,
): string | Animated.AnimatedInterpolation<string> | undefined {
  const progress = useRef<Animated.Value | null>(null);
  if (progress.current == null) progress.current = new Animated.Value(1);
  const state = useRef<{ from: string | undefined; to: string | undefined; changed: number }>({
    from: color,
    to: color,
    changed: 0,
  });
  if (state.current.to !== color) {
    // Restart from the previous target; the half-finished tween is dropped.
    state.current = { from: state.current.to, to: color, changed: state.current.changed + 1 };
    progress.current.setValue(0);
  }
  const changed = state.current.changed;
  useEffect(() => {
    if (changed === 0) return;
    const animation = Animated.timing(progress.current!, {
      toValue: 1,
      duration: durationMs,
      easing: curve.easing,
      useNativeDriver: false,
    });
    animation.start();
    return () => animation.stop();
  }, [changed, durationMs, curve]);
  const { from, to } = state.current;
  if (from == null || to == null || from === to || durationMs <= 0) return to;
  return progress.current.interpolate({ inputRange: [0, 1], outputRange: [from, to] });
}

export interface AnimatedContainerProps extends ViewProps {
  /** Duration in milliseconds. */
  duration: number;
  curve?: Curve;
  style?: StyleProp<ViewStyle>;
  children?: ReactNode;
}

const kTransitionProperties = [
  'background-color',
  'border-color',
  'box-shadow',
  'opacity',
  'width',
  'height',
  'border-radius',
  'transform',
];

/**
 * Port of `AnimatedContainer`: a View whose decoration changes animate.
 *
 * The web uses CSS transitions (colours, shadow, size, radius, opacity);
 * native tweens the background and border colours with `Animated` and applies
 * every other property immediately.
 */
export function AnimatedContainer({ duration, curve = Curves.linear, style, children, ...rest }: AnimatedContainerProps) {
  if (isWeb) {
    return (
      <View
        {...rest}
        style={[
          style,
          webStyle({
            transitionProperty: kTransitionProperties.join(', '),
            transitionDuration: `${duration}ms`,
            transitionTimingFunction: curve.css,
          }),
        ]}
      >
        {children}
      </View>
    );
  }
  return (
    <NativeAnimatedContainer duration={duration} curve={curve} style={style} {...rest}>
      {children}
    </NativeAnimatedContainer>
  );
}

function NativeAnimatedContainer({ duration, curve = Curves.linear, style, children, ...rest }: AnimatedContainerProps) {
  const flat = (StyleSheet.flatten(style) ?? {}) as ViewStyle;
  const background = useTweenedColor(flat.backgroundColor as string | undefined, duration, curve);
  const border = useTweenedColor(flat.borderColor as string | undefined, duration, curve);
  const animatedStyle: Record<string, unknown> = {};
  if (background !== undefined) animatedStyle.backgroundColor = background;
  if (border !== undefined) animatedStyle.borderColor = border;
  return (
    <Animated.View {...rest} style={[flat, animatedStyle as Animated.WithAnimatedObject<ViewStyle>]}>
      {children}
    </Animated.View>
  );
}
