import { Redirect } from 'expo-router';
import { Linking, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Icon, Parcel } from '../components/art';
import { Bob } from '../components/motion';
import { Track } from '../components/track';
import { Badge, Card, Chip, IconCircle, NavBar, Notice, T } from '../components/ui';
import type { Order } from '../lib/api';
import { choiceLabel, modeLabel, money, slotLabel, telLink, timeLabel } from '../lib/format';
import { useStore } from '../lib/store';
import { colors } from '../lib/theme';

const TIMELINE = ['Confirmed', 'Out for delivery', 'Delivered'];

function badgeOf(order: Order): { label: string; tone: 'dark' | 'green' | 'red' | 'light' } {
  switch (order.status) {
    case 'PENDING':
      return { label: 'Confirming', tone: 'dark' };
    case 'CONFIRMED':
      return { label: 'Confirmed', tone: 'light' };
    case 'OUT_FOR_DELIVERY':
      return { label: 'On the way', tone: 'light' };
    case 'DELIVERED':
      return { label: 'Delivered', tone: 'green' };
    default:
      return { label: order.status === 'CANCELLED' ? 'Cancelled' : 'Failed', tone: 'red' };
  }
}

export default function Bookings() {
  const { user, orders, loading, error, refresh } = useStore();
  if (!user) return <Redirect href="/login" />;
  if (user.role === 'admin') return <Redirect href="/admin" />;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <SafeAreaView edges={['top']} style={{ flex: 1 }}>
        <ScrollView
          contentContainerStyle={styles.content}
          refreshControl={<RefreshControl refreshing={loading && orders.length > 0} onRefresh={refresh} tintColor={colors.orange} />}
        >
          <T w="extrabold" size={26} style={{ letterSpacing: -0.6 }}>My bookings</T>
          {error ? <Notice message={error} onRetry={refresh} /> : null}
          {orders.length === 0 ? (
            <View style={styles.empty}>
              <Bob duration={1500}>
                <Parcel size={88} />
              </Bob>
              <T w="extrabold" size={17}>{loading ? 'Loading your bookings…' : 'No bookings yet'}</T>
              {!loading ? (
                <T size={14} color={colors.muted} style={{ textAlign: 'center' }}>Choose a delivery slot from Home and it will show up here.</T>
              ) : null}
            </View>
          ) : (
            orders.map((o, i) => {
              const badge = badgeOf(o);
              const failed = o.status === 'FAILED' || o.status === 'CANCELLED';
              const step = o.status === 'DELIVERED' ? 2 : o.status === 'OUT_FOR_DELIVERY' ? 1 : 0;
              return (
                <Animated.View key={o.orderId} entering={FadeInDown.duration(400).delay(i * 60)}>
                  <Card style={{ gap: 12, opacity: failed ? 0.75 : 1 }}>
                    <View style={styles.row}>
                      <IconCircle name="bag" />
                      <View style={{ flex: 1, gap: 4 }}>
                        <T w="extrabold" size={15} numberOfLines={1}>{o.itemName ?? 'Order'}</T>
                        <T size={12.5} color={colors.muted}>Order {o.orderId.slice(0, 8).toUpperCase()}</T>
                      </View>
                      <Badge label={badge.label} tone={badge.tone} />
                    </View>
                    <View style={styles.grid}>
                      <Field label="Slot" value={slotLabel(o)} wide />
                      <Field label="Amount" value={money(o.amount)} />
                      <Field label="Payment" value={o.paymentChoice ? choiceLabel(o.paymentChoice) : modeLabel(o.paymentMode)} />
                      {o.address ? <Field label="Address" value={o.address} wide /> : null}
                    </View>
                    {o.paymentMode ? <Chip label={modeLabel(o.paymentMode)} cash={o.paymentMode === 'COD'} /> : null}
                    {!failed && o.status !== 'PENDING' ? (
                      <Track step={step} labels={TIMELINE} color={o.status === 'DELIVERED' ? colors.green : '#4C3FD1'} labelColor={colors.lavenderInk} />
                    ) : null}
                    {o.agentName && !failed ? (
                      <Pressable
                        accessibilityRole="button"
                        accessibilityLabel={`Call ${o.agentName}`}
                        disabled={!o.agentPhone}
                        onPress={() => o.agentPhone && Linking.openURL(telLink(o.agentPhone))}
                        style={styles.agent}
                      >
                        <IconCircle name="truck" bg={colors.lavender} color="#3730A3" size={38} />
                        <View style={{ flex: 1 }}>
                          <T w="extrabold" size={14}>{o.agentName}</T>
                          <T size={12.5} color={colors.muted}>
                            {o.status === 'DELIVERED' && o.deliveredAt ? `Delivered at ${timeLabel(o.deliveredAt)}` : o.agentPhone ?? 'Your delivery agent'}
                          </T>
                        </View>
                        {o.agentPhone && o.status !== 'DELIVERED' ? (
                          <View style={styles.call}>
                            <Icon name="phone" size={16} color="#fff" stroke={2.2} />
                          </View>
                        ) : null}
                      </Pressable>
                    ) : null}
                  </Card>
                </Animated.View>
              );
            })
          )}
        </ScrollView>
      </SafeAreaView>
      <NavBar />
    </View>
  );
}

function Field({ label, value, wide }: { label: string; value: string; wide?: boolean }) {
  return (
    <View style={{ width: wide ? '100%' : '47%' }}>
      <T w="semibold" size={12} color={colors.muted}>{label}</T>
      <T w="extrabold" size={14} numberOfLines={wide ? 2 : 1}>{value}</T>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 20, paddingTop: 16, paddingBottom: 130, gap: 14 },
  empty: { alignItems: 'center', gap: 10, marginTop: 80, paddingHorizontal: 30 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, borderTopWidth: 1, borderTopColor: colors.line, paddingTop: 12 },
  agent: { flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: colors.bg, borderRadius: 18, padding: 10 },
  call: { width: 38, height: 38, borderRadius: 19, backgroundColor: colors.green, alignItems: 'center', justifyContent: 'center' },
});
