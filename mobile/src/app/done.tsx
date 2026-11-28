import { LinearGradient } from 'expo-linear-gradient';
import { Redirect, router } from 'expo-router';
import { ScrollView, StyleSheet, View } from 'react-native';
import Animated, { FadeInDown, ZoomIn } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Icon, Van } from '../components/art';
import { Bob, Drive, Pulse } from '../components/motion';
import { Badge, IconCircle, PrimaryButton, T, TextButton } from '../components/ui';
import type { PaymentChoice } from '../lib/api';
import { money, slotLabel } from '../lib/format';
import { useStore } from '../lib/store';
import { colors, gradients, shadow } from '../lib/theme';

const CONFETTI = [
  { l: '8%', t: 70, c: '#FFFFFF', s: 8 }, { l: '22%', t: 140, c: '#7C6CF0', s: 10 }, { l: '35%', t: 60, c: '#141414', s: 6 },
  { l: '62%', t: 90, c: '#16A34A', s: 8 }, { l: '78%', t: 150, c: '#FFFFFF', s: 10 }, { l: '88%', t: 70, c: '#7C6CF0', s: 6 },
  { l: '14%', t: 230, c: '#16A34A', s: 6 }, { l: '84%', t: 240, c: '#141414', s: 8 }, { l: '50%', t: 40, c: '#FFFFFF', s: 6 },
  { l: '70%', t: 270, c: '#FFFFFF', s: 6 }, { l: '28%', t: 280, c: '#141414', s: 8 }, { l: '92%', t: 300, c: '#16A34A', s: 6 },
] as const;

export default function Done() {
  const { user, flow, updateFlow, resetFlow } = useStore();
  if (!user) return <Redirect href="/login" />;
  const order = flow.result;
  const purchase = flow.purchase;
  if (!order || !purchase) return <Redirect href="/home" />;

  const ok = order.status !== 'FAILED' && order.status !== 'CANCELLED';
  const choice: PaymentChoice = order.paymentChoice ?? 'PREPAID';
  const paymentFailed = !ok && choice === 'ONLINE';
  const amount = money(order.amount);

  const paymentLine =
    choice === 'PREPAID' ? `${amount} already paid · held in escrow until delivery`
      : choice === 'ONLINE' ? `${amount} paid · held in escrow until delivery`
        : `Pay ${amount} to the agent on delivery (cash or UPI)`;

  const home = () => {
    resetFlow();
    router.replace('/home');
  };
  /** Back to the slot list for the same purchase, optionally switching how it is paid. */
  const retry = (nextChoice: PaymentChoice | null) => {
    updateFlow({ result: null, slot: null, hold: null, locked: false, paymentChoice: nextChoice });
    router.replace('/slots');
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <LinearGradient colors={ok ? gradients.brand : (['#9CA3AF', '#D1D5DB'] as const)} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.hero}>
        {ok
          ? CONFETTI.map((c, i) => (
              <Animated.View key={i} entering={ZoomIn.delay(i * 50)} style={{ position: 'absolute', left: c.l, top: c.t }}>
                <Bob duration={1100 + (i % 3) * 300} delay={i * 120}>
                  <View style={{ width: c.s, height: c.s, borderRadius: i % 2 ? c.s / 2 : 2, backgroundColor: c.c }} />
                </Bob>
              </Animated.View>
            ))
          : null}
        <Animated.View entering={ZoomIn.springify().damping(12)} style={styles.badge}>
          <Pulse size={104} color="rgba(255,255,255,0.7)" />
          <View style={[styles.mark, { backgroundColor: ok ? colors.green : colors.red }]}>
            <Icon name={ok ? 'check' : 'close'} size={34} color="#fff" stroke={3} />
          </View>
        </Animated.View>
        <Animated.View entering={FadeInDown.duration(500).delay(250)} style={{ alignItems: 'center', gap: 6, paddingHorizontal: 24 }}>
          <T w="extrabold" size={paymentFailed ? 22 : 27} style={{ letterSpacing: -0.5, textAlign: 'center' }}>
            {ok ? 'Delivery scheduled!' : paymentFailed ? 'Payment failed — your slot was released' : "We couldn't confirm that slot"}
          </T>
          <T w="semibold" size={14.5} color={ok ? colors.brown : '#374151'} style={{ textAlign: 'center' }}>
            {ok ? slotLabel(order) : paymentFailed ? 'No money was taken. Try again, or pay when it arrives.' : 'The hold ran out before it was confirmed. Pick another window.'}
          </T>
        </Animated.View>
      </LinearGradient>

      <ScrollView contentContainerStyle={styles.content} style={{ marginTop: -40 }}>
        <Animated.View entering={FadeInDown.duration(500).delay(400)} style={styles.card}>
          <View style={styles.row}>
            <IconCircle name="bag" size={46} />
            <View style={{ flex: 1 }}>
              <T w="extrabold" size={15} numberOfLines={1}>{purchase.itemName}</T>
              <T size={12.5} color={colors.muted}>Order {order.orderId.slice(0, 8).toUpperCase()}</T>
            </View>
            <Badge label={ok ? 'Confirmed' : 'Failed'} tone={ok ? 'green' : 'red'} />
          </View>
          <View style={styles.grid}>
            <Field label="Slot" value={slotLabel(order)} />
            <Field label="Address" value={flow.place?.title ?? order.address ?? ''} />
          </View>
          <View style={styles.payment}>
            <Icon name={choice === 'CASH' ? 'cash' : 'shield'} size={16} color={ok ? colors.greenText : colors.muted} stroke={2.2} />
            <T w="semibold" size={12.5} color={ok ? colors.greenText : colors.muted} style={{ flex: 1 }}>
              {ok ? paymentLine : `${amount} · nothing charged`}
            </T>
          </View>
        </Animated.View>

        {ok ? (
          <Animated.View entering={FadeInDown.duration(500).delay(500)}>
            <LinearGradient colors={gradients.lavender} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.note}>
              <Drive duration={1500} distance={8}>
                <Van width={80} />
              </Drive>
              <T w="semibold" size={12.5} color={colors.lavenderInk} style={{ flex: 1, lineHeight: 19 }}>
                {order.agentName ? `${order.agentName} will bring it in your window.` : 'Agent will be assigned shortly.'}
                {choice === 'CASH' ? ' Keep the amount ready.' : ' The seller is paid only after delivery.'}
              </T>
            </LinearGradient>
          </Animated.View>
        ) : null}
      </ScrollView>

      <SafeAreaView edges={['bottom']} style={styles.footer}>
        {ok ? (
          <>
            <PrimaryButton
              label="View my bookings"
              onPress={() => {
                resetFlow();
                router.replace('/bookings');
              }}
            />
            <TextButton label="Back to home" onPress={home} />
          </>
        ) : paymentFailed ? (
          <>
            <PrimaryButton label="Try again" onPress={() => retry('ONLINE')} />
            <TextButton label="Pay on delivery instead" onPress={() => retry('CASH')} />
          </>
        ) : (
          <>
            <PrimaryButton label="Pick another slot" onPress={() => retry(flow.paymentChoice)} />
            <TextButton label="Back to home" onPress={home} />
          </>
        )}
      </SafeAreaView>
    </View>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <View style={{ flex: 1 }}>
      <T w="semibold" size={12} color={colors.muted}>{label}</T>
      <T w="extrabold" size={14} numberOfLines={1}>{value}</T>
    </View>
  );
}

const styles = StyleSheet.create({
  hero: { height: 380, alignItems: 'center', justifyContent: 'center', gap: 14, paddingBottom: 30, overflow: 'hidden' },
  badge: { width: 104, height: 104, borderRadius: 52, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center', ...shadow, shadowOpacity: 0.25 },
  mark: { width: 64, height: 64, borderRadius: 32, alignItems: 'center', justifyContent: 'center' },
  content: { paddingHorizontal: 20, gap: 14, paddingBottom: 16 },
  card: { backgroundColor: '#fff', borderRadius: 24, padding: 16, gap: 12, ...shadow },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  grid: { flexDirection: 'row', gap: 12, borderTopWidth: 1, borderTopColor: colors.line, paddingTop: 12 },
  payment: { flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: colors.bg, borderRadius: 14, padding: 10 },
  note: { borderRadius: 20, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 12, overflow: 'hidden' },
  footer: { paddingHorizontal: 20, paddingTop: 8, gap: 4 },
});
