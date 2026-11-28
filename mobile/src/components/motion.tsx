import { ReactNode, useEffect } from 'react';
import { StyleProp, ViewStyle } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

type Props = { children?: ReactNode; style?: StyleProp<ViewStyle>; delay?: number; duration?: number };

export function Bob({ children, style, delay = 0, duration = 1500, distance = 6 }: Props & { distance?: number }) {
  const y = useSharedValue(0);
  useEffect(() => {
    y.value = withDelay(
      delay,
      withRepeat(
        withSequence(
          withTiming(-distance, { duration, easing: Easing.inOut(Easing.sin) }),
          withTiming(0, { duration, easing: Easing.inOut(Easing.sin) }),
        ),
        -1,
      ),
    );
  }, [y, delay, duration, distance]);
  const anim = useAnimatedStyle(() => ({ transform: [{ translateY: y.value }] }));
  return <Animated.View style={[style, anim]}>{children}</Animated.View>;
}

export function Drive({ children, style, duration = 2000, distance = 14 }: Props & { distance?: number }) {
  const x = useSharedValue(-distance);
  useEffect(() => {
    x.value = withRepeat(withTiming(distance, { duration, easing: Easing.inOut(Easing.quad) }), -1, true);
  }, [x, duration, distance]);
  const anim = useAnimatedStyle(() => ({ transform: [{ translateX: x.value }] }));
  return <Animated.View style={[style, anim]}>{children}</Animated.View>;
}

export function Pulse({ size, color, style }: { size: number; color: string; style?: StyleProp<ViewStyle> }) {
  const t = useSharedValue(0);
  useEffect(() => {
    t.value = withRepeat(withTiming(1, { duration: 1700, easing: Easing.out(Easing.quad) }), -1);
  }, [t]);
  const anim = useAnimatedStyle(() => ({
    opacity: 0.6 * (1 - t.value),
    transform: [{ scale: 0.7 + t.value * 1.6 }],
  }));
  return (
    <Animated.View
      pointerEvents="none"
      style={[{ position: 'absolute', width: size, height: size, borderRadius: size / 2, backgroundColor: color }, style, anim]}
    />
  );
}

export function Spin({ children, duration = 2000 }: { children: ReactNode; duration?: number }) {
  const r = useSharedValue(0);
  useEffect(() => {
    r.value = withRepeat(withTiming(360, { duration, easing: Easing.linear }), -1);
  }, [r, duration]);
  const anim = useAnimatedStyle(() => ({ transform: [{ rotate: `${r.value}deg` }] }));
  return <Animated.View style={anim}>{children}</Animated.View>;
}

export function Road({ color = '#FFFFFF' }: { color?: string }) {
  const x = useSharedValue(0);
  useEffect(() => {
    x.value = withRepeat(withTiming(-48, { duration: 600, easing: Easing.linear }), -1);
  }, [x]);
  const anim = useAnimatedStyle(() => ({ transform: [{ translateX: x.value }] }));
  return (
    <Animated.View style={[{ flexDirection: 'row', gap: 24, width: 1200, opacity: 0.8 }, anim]}>
      {Array.from({ length: 25 }, (_, i) => (
        <Animated.View key={i} style={{ width: 24, height: 6, borderRadius: 3, backgroundColor: color }} />
      ))}
    </Animated.View>
  );
}
