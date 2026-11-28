import { LinearGradient } from 'expo-linear-gradient';
import { router, usePathname } from 'expo-router';
import { ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleProp, StyleSheet, Text, TextProps, View, ViewStyle } from 'react-native';
import { colors, font, gradients, shadow } from '../lib/theme';
import { Icon, IconName } from './art';

type Weight = 'regular' | 'medium' | 'semibold' | 'bold' | 'extrabold';

export function T({ w = 'medium', size = 15, color = colors.text, style, ...rest }: TextProps & { w?: Weight; size?: number; color?: string }) {
  return <Text {...rest} style={[{ fontFamily: font[w], fontSize: size, color }, style]} />;
}

export function PrimaryButton({ label, onPress, loading, disabled, style }: {
  label: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const off = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: !!off, busy: !!loading }}
      onPress={onPress}
      disabled={off}
      style={({ pressed }) => [styles.primary, { opacity: off ? 0.5 : 1, transform: [{ scale: pressed ? 0.97 : 1 }] }, style]}
    >
      <T w="bold" size={16} color="#fff">{label}</T>
      <LinearGradient colors={gradients.button} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.arrow}>
        {loading ? <ActivityIndicator size="small" color={colors.ink} /> : <Icon name="arrow" size={16} stroke={2.4} />}
      </LinearGradient>
    </Pressable>
  );
}

export function TextButton({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={styles.textButton} hitSlop={8}>
      <T w="bold" size={14.5} color={colors.orangeDark}>{label}</T>
    </Pressable>
  );
}

export function RoundButton({ icon, label, onPress }: { icon: IconName; label: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} style={styles.round}>
      <Icon name={icon} size={20} stroke={2.2} />
    </Pressable>
  );
}

export function Header({ title }: { title: string }) {
  return (
    <View style={styles.header}>
      <RoundButton icon="back" label="Back" onPress={() => (router.canGoBack() ? router.back() : router.replace('/home'))} />
      <T w="extrabold" size={20}>{title}</T>
    </View>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

/** A small payment-mode chip: Prepaid or Cash on delivery. */
export function Chip({ label, cash }: { label: string; cash?: boolean }) {
  return (
    <View style={[styles.chip, { backgroundColor: cash ? '#FEF3C7' : colors.lavender }]}>
      <Icon name={cash ? 'cash' : 'shield'} size={12} color={cash ? '#92400E' : '#3730A3'} stroke={2.2} />
      <T w="bold" size={11} color={cash ? '#92400E' : '#3730A3'}>{label}</T>
    </View>
  );
}

/** A section's own error, so one service being down does not blank the whole page. */
export function ErrorCard({ title, message, onRetry }: { title: string; message: string; onRetry: () => void }) {
  return (
    <View style={styles.errorCard}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        <Icon name="alert" size={18} color="#991B1B" />
        <T w="extrabold" size={14} color="#7F1D1D" style={{ flex: 1 }}>{title}</T>
        <TextButton label="Retry" onPress={onRetry} />
      </View>
      <T size={12.5} color="#7F1D1D">{message}</T>
    </View>
  );
}

export function Badge({ label, tone = 'dark' }: { label: string; tone?: 'dark' | 'green' | 'red' | 'light' }) {
  const map = {
    dark: [colors.ink, '#fff'],
    green: [colors.greenBg, '#166534'],
    red: [colors.redBg, '#991B1B'],
    light: ['#fff', '#3730A3'],
  } as const;
  const [bg, fg] = map[tone];
  return (
    <View style={[styles.badge, { backgroundColor: bg }]}>
      <T w="bold" size={11.5} color={fg}>{label}</T>
    </View>
  );
}

export function Notice({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <View style={styles.notice}>
      <Icon name="alert" size={18} color="#991B1B" />
      <T w="semibold" size={13} color="#7F1D1D" style={{ flex: 1 }}>{message}</T>
      {onRetry ? <TextButton label="Retry" onPress={onRetry} /> : null}
    </View>
  );
}

export function IconCircle({ name, bg = colors.peach, color = '#E8501A', size = 44 }: { name: IconName; bg?: string; color?: string; size?: number }) {
  return (
    <View style={{ width: size, height: size, borderRadius: size / 2, backgroundColor: bg, alignItems: 'center', justifyContent: 'center' }}>
      <Icon name={name} size={size * 0.48} color={color} stroke={1.9} />
    </View>
  );
}

const tabs: { path: '/home' | '/bookings' | '/profile'; icon: IconName; label: string }[] = [
  { path: '/home', icon: 'home', label: 'Home' },
  { path: '/bookings', icon: 'calendar', label: 'My bookings' },
  { path: '/profile', icon: 'user', label: 'Profile' },
];

export function NavBar() {
  const path = usePathname();
  return (
    <View style={styles.nav}>
      {tabs.map((t) => {
        const active = path === t.path;
        const inner = <Icon name={t.icon} size={22} color={active ? colors.ink : '#E5E5E5'} />;
        return (
          <Pressable
            key={t.path}
            accessibilityRole="tab"
            accessibilityLabel={t.label}
            accessibilityState={{ selected: active }}
            onPress={() => !active && router.replace(t.path)}
          >
            {active ? (
              <LinearGradient colors={gradients.button} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.navItem}>
                {inner}
              </LinearGradient>
            ) : (
              <View style={styles.navItem}>{inner}</View>
            )}
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  primary: {
    height: 56,
    borderRadius: 999,
    backgroundColor: colors.ink,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
  },
  arrow: { width: 32, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  textButton: { minHeight: 44, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 8 },
  round: {
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
    ...shadow,
  },
  header: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  card: { backgroundColor: colors.card, borderRadius: 22, padding: 16, ...shadow, shadowOpacity: 0.05 },
  badge: { paddingHorizontal: 10, paddingVertical: 6, borderRadius: 999 },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 4, alignSelf: 'flex-start', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 999 },
  errorCard: { backgroundColor: colors.redBg, borderRadius: 20, padding: 14, gap: 4 },
  notice: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: colors.redBg,
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  nav: {
    position: 'absolute',
    alignSelf: 'center',
    bottom: 22,
    flexDirection: 'row',
    gap: 8,
    padding: 8,
    borderRadius: 999,
    backgroundColor: colors.ink,
    ...shadow,
    shadowOpacity: 0.3,
    elevation: 10,
  },
  navItem: { width: 52, height: 52, borderRadius: 26, alignItems: 'center', justifyContent: 'center' },
});
