import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { Redirect, router, Stack } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { BackHandler, ScrollView, StyleSheet, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Icon, IconName } from '../components/art';
import { Bob, Pulse, Spin } from '../components/motion';
import { IconCircle, Notice, PrimaryButton, RoundButton, T } from '../components/ui';
import { api, ApiError, Order, OrderStatus } from '../lib/api';
import { countdown, dayLabel, money, windowLabel } from '../lib/format';
import { useStore } from '../lib/store';
import { colors, gradients, shadow } from '../lib/theme';

type Step = 'review' | 'paying' | 'confirming';
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const SETTLED: OrderStatus[] = ['CONFIRMED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'CANCELLED'];

export default function Confirm() {
  const { user, flow, updateFlow, refresh } = useStore();
  const [now, setNow] = useState(Date.now());
  const [step, setStep] = useState<Step>('review');
  const [error, setError] = useState('');
  const alive = useRef(true);

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => {
      clearInterval(t);
      alive.current = false;
    };
  }, []);

  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => step !== 'review');
    return () => sub.remove();
  }, [step]);

  if (!user) return <Redirect href="/login" />;
  const { hold, slot, place, date, purchase, paymentChoice: choice } = flow;
  if (!hold || !slot || !place || !date || !purchase || !choice) return <Redirect href="/home" />;

  const online = choice === 'ONLINE';
  const amount = money(purchase.amount);
  const total = 5 * 60;
  const left = Math.round((new Date(hold.expiresAt).getTime() - now) / 1000);
  const expired = left <= 0 && step === 'review';
  const busy = step !== 'review';

  const leave = () => {
    updateFlow({ hold: null, locked: false });
    router.back();
  };

  const confirm = async () => {
    setError('');
    setStep(online ? 'paying' : 'confirming');
    try {
      const created = await api.createOrder(
        {
          customerId: user.customerId,
          purchaseId: purchase.purchaseId,
          holdId: hold.holdId,
          paymentChoice: choice,
          lat: place.latitude,
          lng: place.longitude,
          address: [place.details, place.title, place.subtitle].filter(Boolean).join(', '),
          areaName: slot.areaName,
          slotDate: date,
          startHour: slot.startHour,
          endHour: slot.endHour,
        },
        `order-${hold.holdId}`,
      );
      let order: Order = await api.order(created.orderId);
      for (let i = 0; i < 40 && !SETTLED.includes(order.status) && alive.current; i++) {
        if (online && order.sagaStep && order.sagaStep !== 'PAYMENT_REQUESTED') setStep('confirming');
        await sleep(1000);
        order = await api.order(created.orderId);
      }
      if (!alive.current) return;
      if (!SETTLED.includes(order.status)) {
        setError('Still processing. Check My bookings in a minute.');
        setStep('review');
        return;
      }
      const ok = order.status !== 'FAILED' && order.status !== 'CANCELLED';
      updateFlow({ result: order, hold: null, locked: false });
      refresh();
      Haptics.notificationAsync(ok ? Haptics.NotificationFeedbackType.Success : Haptics.NotificationFeedbackType.Error);
      router.replace('/done');
    } catch (e) {
      if (e instanceof ApiError && e.code === 'ALREADY_SCHEDULED') {
        setError('This order already has a delivery slot. Check My bookings.');
      } else {
        setError(e instanceof ApiError ? e.message : online ? 'Payment could not start. Try again.' : 'Could not confirm the slot. Try again.');
      }
      setStep('review');
    }
  };

  const stages: { label: string; state: 'done' | 'now' | 'next' }[] = online
    ? [
        { label: 'Held', state: 'done' },
        { label: 'Payment', state: step === 'confirming' ? 'done' : 'now' },
        { label: 'Confirmed', state: step === 'confirming' ? 'now' : 'next' },
      ]
    : [
        { label: 'Held', state: 'done' },
        { label: 'Confirmed', state: step === 'confirming' ? 'now' : 'next' },
      ];

  const paymentValue =
    choice === 'PREPAID' ? 'Already paid · held in escrow until delivery'
      : choice === 'CASH' ? `Pay ${amount} on delivery (cash or UPI)`
        : `Pay ${amount} now · held in escrow until delivery`;

  const label = step === 'paying' ? 'Paying…' : step === 'confirming' ? 'Confirming…' : online ? `Pay ${amount} & confirm` : 'Confirm slot';

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <Stack.Screen options={{ gestureEnabled: !busy }} />
      <SafeAreaView edges={['top']} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.content}>
          <View style={styles.header}>
            {busy ? <View style={{ width: 46 }} /> : <RoundButton icon="back" label="Back" onPress={leave} />}
            <T w="extrabold" size={20}>{online ? 'Confirm & pay' : 'Confirm your slot'}</T>
          </View>

          <Animated.View entering={FadeInDown.duration(450)} style={styles.timer}>
            <View style={styles.between}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                <Spin>
                  <Icon name="refresh" size={16} color="#FFA24C" stroke={2.4} />
                </Spin>
                <T w="bold" size={13.5} color="#fff">{expired ? 'Hold expired' : 'Slot held for you'}</T>
              </View>
              <T w="extrabold" size={18} color="#FFB86B">{countdown(left)}</T>
            </View>
            <View style={styles.timerBase}>
              <LinearGradient
                colors={gradients.button}
                start={{ x: 0, y: 0 }}
                end={{ x: 1, y: 0 }}
                style={{ height: 6, borderRadius: 6, width: `${Math.max(0, Math.min(100, (left / total) * 100))}%` }}
              />
            </View>
          </Animated.View>

          <Animated.View entering={FadeInDown.duration(450).delay(80)} style={styles.summary}>
            <Row icon="bag" label="Item" value={purchase.itemName} />
            <Row icon="calendar" label="Date" value={dayLabel(date)} />
            <Row icon="clock" label="Delivery window" value={windowLabel(slot.startHour, slot.endHour)} />
            <Row icon="pin" label="Address" value={place.details ? `${place.details}, ${place.title}` : place.title} />
            <Row icon={choice === 'CASH' ? 'cash' : 'card'} label="Payment" value={paymentValue} last />
          </Animated.View>

          <Animated.View entering={FadeInDown.duration(450).delay(160)}>
            <LinearGradient colors={gradients.lavender} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.escrow}>
              <Bob duration={1500}>
                <Icon name={choice === 'CASH' ? 'cash' : 'shield'} size={32} color="#3730A3" stroke={1.8} />
              </Bob>
              <T w="semibold" size={12.5} color={colors.lavenderInk} style={{ flex: 1, lineHeight: 19 }}>
                {choice === 'CASH'
                  ? 'Nothing to pay now. Hand the amount to the agent, in cash or by UPI, when your order arrives.'
                  : online
                    ? 'Money stays in escrow until delivery. If payment fails, the slot is released automatically.'
                    : 'Your payment is already in escrow and reaches the seller only after delivery.'}
              </T>
            </LinearGradient>
          </Animated.View>

          <Animated.View entering={FadeInDown.duration(450).delay(240)} style={styles.stages}>
            {stages.map((s, i) => (
              <View key={s.label} style={styles.stage}>
                {i < stages.length - 1 ? <View style={[styles.link, { backgroundColor: s.state === 'done' ? colors.green : colors.line }]} /> : null}
                <View style={[styles.stageDot, s.state === 'done' && styles.dotDone, s.state === 'now' && styles.dotNow]}>
                  {s.state === 'done' ? <Icon name="check" size={14} color="#fff" stroke={3} /> : null}
                  {s.state === 'now' ? (
                    <>
                      <Pulse size={30} color={colors.orange} />
                      <View style={styles.nowCore} />
                    </>
                  ) : null}
                </View>
                <T w={s.state === 'next' ? 'semibold' : 'bold'} size={12} color={s.state === 'next' ? colors.muted : colors.text}>{s.label}</T>
              </View>
            ))}
          </Animated.View>

          {error ? <Notice message={error} /> : null}
        </ScrollView>

        <SafeAreaView edges={['bottom']} style={styles.footer}>
          <View>
            <T w="semibold" size={12} color={colors.muted}>{choice === 'PREPAID' ? 'Paid' : online ? 'Pay now' : 'Pay on delivery'}</T>
            <T w="extrabold" size={22}>{amount}</T>
          </View>
          {expired ? (
            <PrimaryButton label="Pick another slot" onPress={leave} style={{ flex: 1 }} />
          ) : (
            <PrimaryButton label={label} onPress={confirm} loading={busy} style={{ flex: 1 }} />
          )}
        </SafeAreaView>
      </SafeAreaView>
    </View>
  );
}

function Row({ icon, label, value, last }: { icon: IconName; label: string; value: string; last?: boolean }) {
  return (
    <View style={[styles.row, !last && styles.rowLine]}>
      <IconCircle name={icon} size={38} />
      <View style={{ flex: 1 }}>
        <T w="semibold" size={12} color={colors.muted}>{label}</T>
        <T w="extrabold" size={14.5} numberOfLines={2}>{value}</T>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 24, gap: 14 },
  header: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  between: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  timer: { backgroundColor: colors.ink, borderRadius: 20, padding: 16, gap: 10 },
  timerBase: { height: 6, borderRadius: 6, backgroundColor: 'rgba(255,255,255,0.15)', overflow: 'hidden' },
  summary: { backgroundColor: '#fff', borderRadius: 22, paddingHorizontal: 16, ...shadow, shadowOpacity: 0.05 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 11 },
  rowLine: { borderBottomWidth: 1, borderBottomColor: colors.line, borderStyle: 'dashed' },
  escrow: { borderRadius: 20, padding: 14, flexDirection: 'row', alignItems: 'center', gap: 12 },
  stages: { flexDirection: 'row', paddingHorizontal: 4 },
  stage: { flex: 1, alignItems: 'center', gap: 6 },
  link: { position: 'absolute', top: 14, left: '50%', width: '100%', height: 3 },
  stageDot: {
    width: 30,
    height: 30,
    borderRadius: 15,
    borderWidth: 3,
    borderColor: colors.line,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  dotDone: { backgroundColor: colors.green, borderColor: colors.green },
  dotNow: { backgroundColor: colors.orange, borderColor: colors.orange },
  nowCore: { width: 10, height: 10, borderRadius: 5, backgroundColor: '#fff' },
  footer: { flexDirection: 'row', alignItems: 'center', gap: 14, paddingHorizontal: 20, paddingTop: 12, paddingBottom: 12, backgroundColor: colors.bg },
});
