import { LinearGradient } from 'expo-linear-gradient';
import { View } from 'react-native';
import { colors, gradients } from '../lib/theme';
import { T } from './ui';

/** Horizontal bars: one labelled row per item, widths relative to the largest value. */
export function HBars({ rows }: { rows: { label: string; value: number; hint?: string }[] }) {
  const max = Math.max(1, ...rows.map((r) => r.value));
  return (
    <View style={{ gap: 10 }}>
      {rows.map((r) => (
        <View key={r.label} style={{ gap: 4 }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
            <T w="semibold" size={12.5} numberOfLines={1} style={{ flex: 1 }}>{r.label}</T>
            <T w="extrabold" size={12.5}>{r.hint ?? r.value}</T>
          </View>
          <View style={{ height: 8, borderRadius: 8, backgroundColor: 'rgba(20,20,20,0.07)', overflow: 'hidden' }}>
            <LinearGradient
              colors={gradients.button}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 0 }}
              style={{ width: `${(r.value / max) * 100}%`, height: 8, borderRadius: 8 }}
            />
          </View>
        </View>
      ))}
    </View>
  );
}

/** Vertical bars over a fixed set of columns, e.g. hours of the day. */
export function Columns({ columns, height = 120 }: { columns: { label: string; value: number }[]; height?: number }) {
  const max = Math.max(1, ...columns.map((c) => c.value));
  return (
    <View>
      <View style={{ height, flexDirection: 'row', alignItems: 'flex-end', gap: 4 }}>
        {columns.map((c) => (
          <View key={c.label} style={{ flex: 1, alignItems: 'center', justifyContent: 'flex-end', height }}>
            {c.value > 0 ? <T w="bold" size={9.5} color={colors.muted}>{c.value}</T> : null}
            <LinearGradient
              colors={gradients.button}
              start={{ x: 0, y: 1 }}
              end={{ x: 0, y: 0 }}
              style={{ width: '100%', height: Math.max(2, ((height - 16) * c.value) / max), borderRadius: 4, marginTop: 2 }}
            />
          </View>
        ))}
      </View>
      <View style={{ flexDirection: 'row', gap: 4, marginTop: 6 }}>
        {columns.map((c, i) => (
          <T key={c.label} w="semibold" size={9.5} color={colors.muted} style={{ flex: 1, textAlign: 'center' }}>
            {i % 2 === 0 ? c.label : ''}
          </T>
        ))}
      </View>
    </View>
  );
}

/** Capacity against booked for one row: a full-width track with the booked share filled. */
export function CapacityBar({ label, capacity, booked }: { label: string; capacity: number; booked: number }) {
  const pct = capacity > 0 ? Math.round((booked / capacity) * 1000) / 10 : 0;
  return (
    <View style={{ gap: 4 }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
        <T w="semibold" size={12.5} style={{ flex: 1 }}>{label}</T>
        <T w="bold" size={12} color={colors.muted}>{booked} / {capacity} · {pct}%</T>
      </View>
      <View style={{ height: 8, borderRadius: 8, backgroundColor: colors.lavender, overflow: 'hidden' }}>
        <View style={{ width: `${Math.min(100, pct)}%`, height: 8, borderRadius: 8, backgroundColor: '#4C3FD1' }} />
      </View>
    </View>
  );
}
