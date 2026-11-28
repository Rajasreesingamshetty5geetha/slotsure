import { StyleSheet, View } from 'react-native';
import { Icon } from './art';
import { Pulse } from './motion';
import { T } from './ui';

export const ORDER_STEPS = ['Ordered', 'Slot', 'Out', 'Delivered'];

/** A rail of steps with the truck on the current one; step is the index into labels. */
export function Track({ step, color, labelColor, labels = ORDER_STEPS }: {
  step: number;
  color: string;
  labelColor: string;
  labels?: string[];
}) {
  const last = labels.length - 1;
  return (
    <View style={{ marginTop: 16 }}>
      <View style={styles.rail}>
        <View style={[StyleSheet.absoluteFill, styles.base]} />
        <View style={[styles.fill, { width: `${(Math.min(step, last) / last) * 100}%`, backgroundColor: color }]} />
        {labels.map((_, i) => {
          const left = `${(i / last) * 100}%` as const;
          if (i === step) {
            return (
              <View key={i} style={[styles.current, { left, backgroundColor: color }]}>
                <Pulse size={28} color={color} />
                <Icon name={i === last ? 'check' : 'truck'} size={14} color="#fff" stroke={2.2} />
              </View>
            );
          }
          return <View key={i} style={[styles.dot, { left, backgroundColor: i < step ? color : 'rgba(20,20,20,0.15)' }]} />;
        })}
      </View>
      <View style={styles.labels}>
        {labels.map((l) => (
          <T key={l} w="semibold" size={11.5} color={labelColor}>{l}</T>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  rail: { height: 6, marginHorizontal: 14 },
  base: { borderRadius: 6, backgroundColor: 'rgba(20,20,20,0.10)' },
  fill: { position: 'absolute', left: 0, top: 0, bottom: 0, borderRadius: 6 },
  dot: { position: 'absolute', top: -3, width: 12, height: 12, marginLeft: -6, borderRadius: 6 },
  current: {
    position: 'absolute',
    top: -11,
    width: 28,
    height: 28,
    marginLeft: -14,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 3,
    borderColor: 'rgba(255,255,255,0.7)',
  },
  labels: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 12 },
});
