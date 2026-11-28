import * as Crypto from 'expo-crypto';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { Redirect, router } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import Animated, { FadeInDown, LinearTransition, ZoomIn } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Icon, Van } from '../components/art';
import { Drive } from '../components/motion';
import { Header, Notice, PrimaryButton, T } from '../components/ui';
import { api, ApiError, Slot } from '../lib/api';
import { addDays, isoDate, modeLabel, money, windowLabel } from '../lib/format';
import { useStore } from '../lib/store';
import { colors, gradients, shadow } from '../lib/theme';

const today = new Date();
const DAYS = [0, 1, 2].map((i) => {
  const d = addDays(today, i);
  return {
    date: isoDate(d),
    label: i === 0 ? 'Today' : i === 1 ? 'Tomorrow' : d.toLocaleDateString('en-IN', { weekday: 'long' }),
    sub: d.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric' }),
  };
});

export default function Slots() {
  const { user, flow, updateFlow } = useStore();
  const [date, setDate] = useState(flow.date ?? DAYS[0].date);
  const [slots, setSlots] = useState<Slot[] | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [holding, setHolding] = useState(false);
  const [error, setError] = useState('');
  const [holdKey, setHoldKey] = useState(() => Crypto.randomUUID());

  const place = flow.place;
  const purchase = flow.purchase;

  const load = useCallback(async () => {
    if (!place) return;
    setLoading(true);
    setError('');
    try {
      const list = await api.slots(place.latitude, place.longitude, date);
      setSlots(list.sort((a, b) => a.startHour - b.startHour));
    } catch (e) {
      setSlots(null);
      setError(e instanceof ApiError ? e.message : 'Could not load slots.');
    } finally {
      setLoading(false);
    }
  }, [place, date]);

  useEffect(() => {
    setSelected(null);
    load();
  }, [load]);

  const visible = useMemo(() => {
    if (!slots) return [];
    const nowHour = new Date().getHours();
    return date === DAYS[0].date ? slots.filter((s) => s.startHour > nowHour) : slots;
  }, [slots, date]);

  if (!user) return <Redirect href="/login" />;
  if (!purchase) return <Redirect href="/home" />;
  if (!place) return <Redirect href="/location" />;

  const chosen = visible.find((s) => s.slotId === selected) ?? null;

  const hold = async () => {
    if (!chosen) return;
    setHolding(true);
    setError('');
    try {
      const h = await api.hold(chosen.slotId, user.customerId, holdKey);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      setHoldKey(Crypto.randomUUID());
      if (purchase.paymentMode === 'PREPAID') {
        // Already paid at checkout: no payment step.
        updateFlow({ date, slot: chosen, hold: h, locked: true, paymentChoice: 'PREPAID' });
        router.push('/confirm');
      } else {
        updateFlow({ date, slot: chosen, hold: h, locked: true, paymentChoice: flow.paymentChoice === 'PREPAID' ? null : flow.paymentChoice });
        router.push('/payment');
      }
    } catch (e) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      if (e instanceof ApiError && e.code === 'SLOT_FULL') {
        setError('That slot just filled up. Pick another one.');
        setSelected(null);
        setHoldKey(Crypto.randomUUID());
        load();
      } else if (e instanceof ApiError && e.code === 'NOT_SERVICEABLE') {
        setError("We don't deliver to this address yet.");
      } else {
        setError(e instanceof ApiError ? e.message : 'Could not hold the slot.');
      }
    } finally {
      setHolding(false);
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <SafeAreaView edges={['top']} style={{ flex: 1 }}>
        <ScrollView
          contentContainerStyle={styles.content}
          refreshControl={<RefreshControl refreshing={loading && slots !== null} onRefresh={load} tintColor={colors.orange} />}
        >
          <Header title="Choose a slot" />

          <Animated.View entering={FadeInDown.duration(500)}>
            <LinearGradient colors={gradients.brand} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.hero}>
              <T w="bold" size={12} color={colors.brown}>Delivering to</T>
              <T w="extrabold" size={15.5} numberOfLines={1} style={{ marginTop: 2, marginRight: 90 }}>{place.title}</T>
              <T size={12.5} color={colors.brown} style={{ marginTop: 6, marginRight: 90 }} numberOfLines={1}>
                {purchase.itemName} · {money(purchase.amount)} · {modeLabel(purchase.paymentMode)}
              </T>
              <Pressable accessibilityRole="button" onPress={() => router.back()} style={styles.change}>
                <T w="bold" size={12} color="#fff">Change address</T>
              </Pressable>
              <Drive style={{ position: 'absolute', right: -30, bottom: -4 }}>
                <Van width={150} />
              </Drive>
            </LinearGradient>
          </Animated.View>

          <View style={styles.days}>
            {DAYS.map((d) => {
              const on = d.date === date;
              const body = (
                <>
                  <T w="extrabold" size={14}>{d.label}</T>
                  <T w="semibold" size={12} style={{ marginTop: 2 }}>{d.sub}</T>
                </>
              );
              return (
                <Pressable key={d.date} accessibilityRole="button" accessibilityState={{ selected: on }} onPress={() => setDate(d.date)} style={{ flex: 1 }}>
                  {on ? (
                    <LinearGradient colors={gradients.button} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={[styles.day, styles.dayOn]}>
                      {body}
                    </LinearGradient>
                  ) : (
                    <View style={styles.day}>{body}</View>
                  )}
                </Pressable>
              );
            })}
          </View>

          <View style={styles.between}>
            <T w="extrabold" size={16}>Delivery windows</T>
            <T w="semibold" size={12} color={colors.muted}>Live capacity</T>
          </View>

          {error ? <Notice message={error} onRetry={load} /> : null}

          {slots === null && loading ? (
            <View style={styles.grid}>
              {Array.from({ length: 6 }, (_, i) => (
                <View key={i} style={[styles.slot, styles.skeleton]} />
              ))}
            </View>
          ) : slots !== null && visible.length === 0 ? (
            <View style={styles.empty}>
              <Icon name="alert" size={28} color={colors.muted} />
              <T w="extrabold" size={16}>{slots.length === 0 ? "We don't deliver here yet" : 'No more slots today'}</T>
              <T size={13.5} color={colors.muted} style={{ textAlign: 'center' }}>
                {slots.length === 0 ? 'Try an address inside our Bengaluru service area.' : 'Pick tomorrow to see the next windows.'}
              </T>
            </View>
          ) : (
            <View style={styles.grid}>
              {visible.map((s, i) => (
                <SlotCard key={s.slotId} slot={s} index={i} selected={s.slotId === selected} onPress={() => setSelected(s.slotId)} />
              ))}
            </View>
          )}
        </ScrollView>

        <SafeAreaView edges={['bottom']} style={styles.footer}>
          <PrimaryButton
            label={chosen ? `Hold ${windowLabel(chosen.startHour, chosen.endHour)}` : 'Select a window'}
            onPress={hold}
            loading={holding}
            disabled={!chosen}
          />
        </SafeAreaView>
      </SafeAreaView>
    </View>
  );
}

function SlotCard({ slot, index, selected, onPress }: { slot: Slot; index: number; selected: boolean; onPress: () => void }) {
  const full = slot.remaining <= 0;
  const low = !full && slot.remaining <= 2;
  const used = Math.round(((slot.capacity - slot.remaining) / slot.capacity) * 100);
  const status = full ? 'Full' : low ? `Only ${slot.remaining} left` : `${slot.remaining} of ${slot.capacity} left`;
  const statusColor = full ? colors.muted : low ? colors.amberText : colors.greenText;
  const bar = full ? (['#9CA3AF', '#9CA3AF'] as const) : low ? (['#F59E0B', '#EF4444'] as const) : (['#34D399', '#16A34A'] as const);

  return (
    <Animated.View entering={FadeInDown.duration(350).delay(index * 40)} layout={LinearTransition} style={styles.slotWrap}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${windowLabel(slot.startHour, slot.endHour)}, ${status}`}
        accessibilityState={{ selected, disabled: full }}
        disabled={full}
        onPress={() => {
          Haptics.selectionAsync();
          onPress();
        }}
        style={({ pressed }) => [
          styles.slot,
          selected && styles.slotOn,
          { opacity: full ? 0.55 : 1, transform: [{ translateY: selected ? -2 : 0 }, { scale: pressed ? 0.97 : 1 }] },
        ]}
      >
        {selected ? <LinearGradient colors={gradients.peach} style={[StyleSheet.absoluteFill, { borderRadius: 16 }]} /> : null}
        <View style={styles.between}>
          <T w="extrabold" size={14.5}>{windowLabel(slot.startHour, slot.endHour)}</T>
          {selected ? (
            <Animated.View entering={ZoomIn.springify()} style={styles.check}>
              <Icon name="check" size={13} color="#fff" stroke={3} />
            </Animated.View>
          ) : null}
        </View>
        <T w="bold" size={12.5} color={statusColor}>{status}</T>
        <View style={styles.barBase}>
          <LinearGradient colors={bar} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={{ width: `${used}%`, height: 6, borderRadius: 6 }} />
        </View>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 24, gap: 14 },
  hero: { borderRadius: 22, padding: 16, overflow: 'hidden' },
  change: { alignSelf: 'flex-start', marginTop: 10, backgroundColor: colors.ink, borderRadius: 999, paddingHorizontal: 12, paddingVertical: 8 },
  days: { flexDirection: 'row', gap: 10 },
  day: { height: 60, borderRadius: 18, alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff', ...shadow, shadowOpacity: 0.04 },
  dayOn: { shadowColor: '#E8501A', shadowOpacity: 0.3, elevation: 6 },
  between: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  slotWrap: { width: '48.4%' },
  slot: {
    minHeight: 92,
    borderRadius: 18,
    padding: 13,
    gap: 8,
    backgroundColor: '#fff',
    borderWidth: 2,
    borderColor: 'transparent',
    overflow: 'hidden',
    ...shadow,
    shadowOpacity: 0.04,
  },
  slotOn: { borderColor: colors.ink, shadowColor: '#E8501A', shadowOpacity: 0.2, elevation: 6 },
  skeleton: { width: '48.4%', backgroundColor: '#ECE8E3' },
  check: { width: 22, height: 22, borderRadius: 11, backgroundColor: colors.ink, alignItems: 'center', justifyContent: 'center' },
  barBase: { height: 6, borderRadius: 6, backgroundColor: 'rgba(20,20,20,0.08)', overflow: 'hidden' },
  empty: { alignItems: 'center', gap: 8, paddingVertical: 32, paddingHorizontal: 24 },
  footer: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 12, backgroundColor: colors.bg },
});

