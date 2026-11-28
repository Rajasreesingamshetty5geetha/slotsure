import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';
import { ReactNode, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Icon, IconName } from '../../components/art';
import { CapacityBar, Columns, HBars } from '../../components/charts';
import { Badge, Card, Chip, ErrorCard, IconCircle, RoundButton, T } from '../../components/ui';
import { api, ApiError, Order } from '../../lib/api';
import { addDays, hourLabel, isoDate, modeLabel, money, slotLabel, windowLabel } from '../../lib/format';
import { Resource, useResource } from '../../lib/hooks';
import { useStore } from '../../lib/store';
import { colors, gradients } from '../../lib/theme';

const HOURS = Array.from({ length: 14 }, (_, i) => 8 + i);
const RECENT = 20;

export default function AdminDashboard() {
  const { user, logout } = useStore();
  const [day, setDay] = useState<'today' | 'yesterday'>('today');
  const date = isoDate(day === 'today' ? new Date() : addDays(new Date(), -1));

  const summary = useResource(() => api.admin.summary(date), [date]);
  const orders = useResource(() => api.admin.orders(date), [date]);
  const slots = useResource(() => api.admin.slots(date), [date]);
  const dispatch = useResource(() => api.admin.dispatch(date), [date]);
  const agents = useResource(() => api.admin.agents(date), [date]);
  const payments = useResource(() => api.admin.payments(date), [date]);
  const all = [summary, orders, slots, dispatch, agents, payments];

  const reloadAll = () => Promise.all(all.map((r) => r.reload())).then(() => undefined);
  const refreshing = all.some((r) => r.loading) && all.some((r) => r.data !== null);

  const signOut = () => {
    logout();
    router.replace('/login');
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <SafeAreaView edges={['top']} style={{ flex: 1 }}>
        <ScrollView
          contentContainerStyle={styles.content}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={reloadAll} tintColor={colors.orange} />}
        >
          <View style={styles.top}>
            <View style={{ flex: 1 }}>
              <T w="semibold" size={13} color={colors.muted}>{user?.email}</T>
              <T w="extrabold" size={26} style={{ letterSpacing: -0.6 }}>Operations</T>
            </View>
            <RoundButton icon="logout" label="Log out" onPress={signOut} />
          </View>

          <View style={styles.pills}>
            {(['today', 'yesterday'] as const).map((d) => {
              const on = d === day;
              const label = d === 'today' ? 'Today' : 'Yesterday';
              return (
                <Pressable key={d} accessibilityRole="tab" accessibilityState={{ selected: on }} onPress={() => setDay(d)} style={{ flex: 1 }}>
                  {on ? (
                    <LinearGradient colors={gradients.button} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.pill}>
                      <T w="extrabold" size={14}>{label}</T>
                    </LinearGradient>
                  ) : (
                    <View style={[styles.pill, styles.pillOff]}>
                      <T w="bold" size={14} color={colors.muted}>{label}</T>
                    </View>
                  )}
                </Pressable>
              );
            })}
          </View>

          <Section title="Bookings" resource={summary} delay={0}>
            {(s) => (
              <View style={styles.tiles}>
                <Tile icon="calendar" label="Total bookings" value={String(s.totalBookings)} />
                <Tile icon="check" label="Confirmed" value={String(s.confirmed)} tone="lavender" />
                <Tile icon="truck" label="Delivered" value={String(s.delivered)} tone="green" />
                <Tile icon="alert" label="Failed" value={String(s.failed)} tone="red" />
              </View>
            )}
          </Section>

          <Section title="Money" resource={payments} delay={60}>
            {(p) => (
              <View style={styles.tiles}>
                <Tile icon="card" label="Prepaid captured" value={money(p.prepaidCaptured)} />
                <Tile icon="shield" label="Escrow held" value={money(p.escrowHeld)} tone="lavender" />
                <Tile icon="cash" label="COD expected" value={summary.data ? money(summary.data.codExpected) : '—'} tone="amber" />
                <Tile icon="cash" label="COD collected" value={money(p.codCollected)} tone="green" />
                <T w="semibold" size={12} color={Number(p.ledgerDifference) === 0 ? colors.greenText : colors.red} style={{ width: '100%' }}>
                  {Number(p.ledgerDifference) === 0 ? 'Ledger balanced · difference ₹0' : `Ledger off by ${money(p.ledgerDifference)}`}
                </T>
              </View>
            )}
          </Section>

          <Section title="Top items" resource={summary} delay={120}>
            {(s) =>
              s.topItems.length === 0 ? <Empty /> : <HBars rows={s.topItems.map((i) => ({ label: i.itemName, value: i.count }))} />
            }
          </Section>

          <Section title="Bookings by hour" resource={summary} delay={180}>
            {(s) => (
              <Columns
                columns={HOURS.map((h) => ({ label: hourLabel(h).replace(' ', ''), value: s.byHour.find((b) => b.startHour === h)?.count ?? 0 }))}
              />
            )}
          </Section>

          <Section
            title="Slots by area"
            resource={slots}
            delay={240}
            aside={slots.data ? `${slots.data.utilisationPct}% booked` : undefined}
          >
            {(s) =>
              s.byArea.length === 0 ? (
                <Empty />
              ) : (
                <View style={{ gap: 12 }}>
                  <T size={12.5} color={colors.muted}>{s.totalBooked} of {s.totalCapacity} places booked</T>
                  {s.byArea.map((a) => (
                    <CapacityBar key={a.areaName} label={a.areaName} capacity={a.capacity} booked={a.booked} />
                  ))}
                </View>
              )
            }
          </Section>

          <Section title="Busiest slots" resource={slots} delay={300}>
            {(s) =>
              s.busiest.length === 0 ? (
                <Empty />
              ) : (
                <View style={{ gap: 2 }}>
                  {s.busiest.map((b, i) => (
                    <View key={`${b.areaName}-${b.startHour}`} style={[styles.line, i > 0 && styles.lineTop]}>
                      <T w="extrabold" size={14} color={colors.orangeDark} style={{ width: 24 }}>{i + 1}</T>
                      <View style={{ flex: 1 }}>
                        <T w="extrabold" size={14}>{b.areaName}</T>
                        <T size={12.5} color={colors.muted}>{windowLabel(b.startHour, b.endHour)}</T>
                      </View>
                      <T w="bold" size={13}>{b.booked} / {b.capacity}</T>
                    </View>
                  ))}
                </View>
              )
            }
          </Section>

          <Section
            title="Agents"
            resource={agents}
            delay={360}
            aside={dispatch.data ? `${dispatch.data.agentsActive} of ${dispatch.data.agentsTotal} on the road` : undefined}
          >
            {(list) => (
              <View style={{ gap: 2 }}>
                {dispatch.error ? <T size={12} color={colors.red}>Dispatch totals unavailable: {dispatch.error}</T> : null}
                {dispatch.data ? (
                  <T size={12.5} color={colors.muted} style={{ marginBottom: 6 }}>
                    {dispatch.data.assigned} assigned · {dispatch.data.delivered} delivered
                  </T>
                ) : null}
                {list.map((a, i) => (
                  <View key={a.agentId} style={[styles.line, i > 0 && styles.lineTop]}>
                    <View style={[styles.activeDot, { backgroundColor: a.active === false ? '#D1D5DB' : colors.green }]} />
                    <View style={{ flex: 1 }}>
                      <T w="extrabold" size={14}>{a.name}</T>
                      <T size={12.5} color={colors.muted}>{a.homeArea}</T>
                    </View>
                    <T w="bold" size={13}>{a.delivered} / {a.assigned}</T>
                  </View>
                ))}
              </View>
            )}
          </Section>

          <Section title="Recent orders" resource={orders} delay={420} aside={orders.data ? `${orders.data.length} orders` : undefined}>
            {(list) =>
              list.length === 0 ? (
                <Empty />
              ) : (
                <View style={{ gap: 10 }}>
                  {list.slice(0, RECENT).map((o) => (
                    <OrderRow
                      key={o.orderId}
                      order={o}
                      onDelivered={() => {
                        // Delivery reaches order-service through Kafka; give it a moment before reloading.
                        setTimeout(() => {
                          orders.reload();
                          summary.reload();
                          agents.reload();
                          dispatch.reload();
                          payments.reload();
                        }, 1500);
                      }}
                    />
                  ))}
                </View>
              )
            }
          </Section>
        </ScrollView>
      </SafeAreaView>
    </View>
  );
}

function Section<T>({ title, resource, children, delay, aside }: {
  title: string;
  resource: Resource<T>;
  children: (data: T) => ReactNode;
  delay: number;
  aside?: string;
}) {
  if (resource.error) {
    return <ErrorCard title={`${title} unavailable`} message={resource.error} onRetry={resource.reload} />;
  }
  return (
    <Animated.View entering={FadeInDown.duration(400).delay(delay)}>
      <Card style={{ gap: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <T w="extrabold" size={16} style={{ flex: 1 }}>{title}</T>
          {aside ? <T w="semibold" size={12} color={colors.muted}>{aside}</T> : null}
        </View>
        {resource.data === null ? <ActivityIndicator color={colors.orange} style={{ paddingVertical: 16 }} /> : children(resource.data)}
      </Card>
    </Animated.View>
  );
}

function Tile({ icon, label, value, tone = 'peach' }: {
  icon: IconName;
  label: string;
  value: string;
  tone?: 'peach' | 'lavender' | 'green' | 'red' | 'amber';
}) {
  const look = {
    peach: [colors.peach, '#E8501A'],
    lavender: [colors.lavender, '#3730A3'],
    green: [colors.greenBg, colors.greenText],
    red: [colors.redBg, colors.red],
    amber: ['#FEF3C7', '#92400E'],
  }[tone];
  return (
    <View style={[styles.tile, { backgroundColor: look[0] }]}>
      <Icon name={icon} size={18} color={look[1]} stroke={2.2} />
      <T w="extrabold" size={20} numberOfLines={1} adjustsFontSizeToFit>{value}</T>
      <T w="semibold" size={12} color={colors.muted}>{label}</T>
    </View>
  );
}

function OrderRow({ order, onDelivered }: { order: Order; onDelivered: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const status = done ? 'DELIVERED' : order.status;
  const tone = status === 'DELIVERED' ? 'green' : status === 'FAILED' || status === 'CANCELLED' ? 'red' : status === 'OUT_FOR_DELIVERY' ? 'light' : 'dark';

  const markDelivered = async () => {
    setBusy(true);
    setError('');
    try {
      await api.admin.complete(order.orderId, `complete-${order.orderId}`);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      setDone(true);
      onDelivered();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not mark it delivered.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <View style={styles.order}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
        <IconCircle name="bag" size={36} />
        <View style={{ flex: 1 }}>
          <T w="extrabold" size={13.5} numberOfLines={1}>{order.itemName ?? 'Order'} · {money(order.amount)}</T>
          <T size={12} color={colors.muted} numberOfLines={1}>
            {order.areaName ?? '—'} · {slotLabel(order)}
          </T>
        </View>
        <Badge label={status.replace(/_/g, ' ').toLowerCase()} tone={tone} />
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        {order.paymentMode ? <Chip label={modeLabel(order.paymentMode)} cash={order.paymentMode === 'COD'} /> : null}
        {order.agentName ? <T size={12} color={colors.muted} numberOfLines={1} style={{ flex: 1 }}>{order.agentName}</T> : <View style={{ flex: 1 }} />}
        {status === 'OUT_FOR_DELIVERY' ? (
          <Pressable accessibilityRole="button" disabled={busy} onPress={markDelivered} style={[styles.deliver, busy && { opacity: 0.6 }]}>
            {busy ? <ActivityIndicator size="small" color="#fff" /> : <Icon name="check" size={14} color="#fff" stroke={3} />}
            <T w="bold" size={12.5} color="#fff">Mark delivered</T>
          </Pressable>
        ) : null}
      </View>
      {error ? <T size={12} color={colors.red}>{error}</T> : null}
    </View>
  );
}

function Empty() {
  return <T size={13} color={colors.muted}>Nothing for this day yet.</T>;
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 40, gap: 14 },
  top: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  pills: { flexDirection: 'row', gap: 10 },
  pill: { height: 46, borderRadius: 999, alignItems: 'center', justifyContent: 'center' },
  pillOff: { backgroundColor: '#fff' },
  tiles: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  tile: { width: '48%', borderRadius: 18, padding: 12, gap: 4 },
  line: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 9 },
  lineTop: { borderTopWidth: 1, borderTopColor: colors.line },
  activeDot: { width: 10, height: 10, borderRadius: 5 },
  order: { backgroundColor: colors.bg, borderRadius: 18, padding: 12, gap: 10 },
  deliver: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: colors.ink,
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
});
