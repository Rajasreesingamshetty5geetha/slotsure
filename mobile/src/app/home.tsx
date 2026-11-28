import { LinearGradient } from 'expo-linear-gradient';
import { Redirect, router } from 'expo-router';
import { Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Icon, IconName, Parcel, Van } from '../components/art';
import { Bob } from '../components/motion';
import { Track } from '../components/track';
import { Badge, Chip, IconCircle, NavBar, Notice, RoundButton, T } from '../components/ui';
import type { Order, Purchase } from '../lib/api';
import { greeting, modeLabel, money, purchaseStatus } from '../lib/format';
import { useStore } from '../lib/store';
import { colors, gradients } from '../lib/theme';

const CATEGORY_ICON: Record<string, IconName> = { Electronics: 'headphones' };

/** Where a purchase sits on Ordered -> Slot -> Out -> Delivered. */
function stepOf(purchase: Purchase, order: Order | undefined) {
  if (purchase.status === 'DELIVERED') return 3;
  if (order?.status === 'OUT_FOR_DELIVERY') return 2;
  if (purchase.status === 'SCHEDULED') return 1;
  return 0;
}

export default function Home() {
  const { user, flow, purchases, orders, loading, error, refresh, startFlow, unread } = useStore();
  if (!user) return <Redirect href="/login" />;
  if (user.role === 'admin') return <Redirect href="/admin" />;

  const orderOf = (p: Purchase) => orders.find((o) => o.orderId === p.orderId);
  const waiting = purchases.filter((p) => p.status === 'AWAITING_SLOT');

  const book = (purchase: Purchase) => {
    startFlow(purchase);
    router.push('/location');
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <SafeAreaView edges={['top']} style={{ flex: 1 }}>
        <ScrollView
          contentContainerStyle={styles.content}
          showsVerticalScrollIndicator={false}
          refreshControl={<RefreshControl refreshing={loading && purchases.length > 0} onRefresh={refresh} tintColor={colors.orange} />}
        >
          <Animated.View entering={FadeInDown.duration(500)} style={styles.top}>
            <View style={{ flex: 1 }}>
              <T w="semibold" size={13} color={colors.muted}>Deliver to</T>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 2 }}>
                <Icon name="pin" size={16} color="#E8501A" stroke={2.2} />
                <T w="extrabold" size={16} numberOfLines={1} style={{ flexShrink: 1 }}>
                  {flow.place ? flow.place.title : 'Chosen when you book a slot'}
                </T>
              </View>
            </View>
            <View>
              <RoundButton icon="bell" label={unread ? `Notifications, ${unread} unread` : 'Notifications'} onPress={() => router.push('/inbox')} />
              {unread > 0 ? (
                <View style={styles.count} pointerEvents="none">
                  <T w="extrabold" size={10.5} color="#fff">{unread > 9 ? '9+' : unread}</T>
                </View>
              ) : null}
            </View>
          </Animated.View>

          <Animated.View entering={FadeInDown.duration(500).delay(60)}>
            <T w="extrabold" size={26} style={{ letterSpacing: -0.6, lineHeight: 32 }}>
              {greeting()}, {user.name}.{'\n'}When should we come by?
            </T>
          </Animated.View>

          {error ? <Notice message={error} onRetry={refresh} /> : null}

          {waiting.length > 0 ? (
            <Animated.View entering={FadeInDown.duration(500).delay(120)}>
              <Pressable accessibilityRole="button" accessibilityLabel="Choose your delivery slot" onPress={() => book(waiting[0])}>
                <LinearGradient colors={gradients.brand} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.banner}>
                  <View style={styles.bannerTick}>
                    <Icon name="check" size={16} color="#fff" stroke={3} />
                  </View>
                  <T w="extrabold" size={17} style={{ marginTop: 10, marginRight: 110 }}>Your order is placed successfully</T>
                  <T w="semibold" size={13} color={colors.brown} style={{ marginTop: 4, marginRight: 110 }}>
                    {waiting.length === 1 ? waiting[0].itemName : `${waiting.length} items`} waiting for a delivery time
                  </T>
                  <View style={styles.bannerCta}>
                    <T w="bold" size={13} color="#fff">Choose your delivery slot</T>
                    <Icon name="arrow" size={14} color="#fff" stroke={2.4} />
                  </View>
                  <Bob style={{ position: 'absolute', right: -20, bottom: 8 }} duration={1300}>
                    <Van width={130} />
                  </Bob>
                </LinearGradient>
              </Pressable>
            </Animated.View>
          ) : null}

          <T w="extrabold" size={17}>Your orders</T>

          {purchases.length === 0 && loading ? (
            <View style={{ gap: 12 }}>
              {[0, 1, 2].map((i) => (
                <View key={i} style={styles.skeleton} />
              ))}
            </View>
          ) : purchases.length === 0 && !error ? (
            <View style={styles.empty}>
              <Bob duration={1500}>
                <Parcel size={72} />
              </Bob>
              <T w="extrabold" size={16}>No orders yet</T>
            </View>
          ) : (
            purchases.map((p, i) => {
              const order = orderOf(p);
              const awaiting = p.status === 'AWAITING_SLOT';
              const delivered = p.status === 'DELIVERED';
              return (
                <Animated.View key={p.purchaseId} entering={FadeInDown.duration(450).delay(160 + i * 60)}>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={`${p.itemName}, ${purchaseStatus(p, order)}`}
                    onPress={() => (awaiting ? book(p) : router.replace('/bookings'))}
                  >
                    <LinearGradient
                      colors={awaiting ? gradients.peach : gradients.lavender}
                      start={{ x: 0, y: 0 }}
                      end={{ x: 1, y: 1 }}
                      style={styles.order}
                    >
                      <View style={styles.row}>
                        <IconCircle name={CATEGORY_ICON[p.category] ?? 'bag'} bg="#fff" color={awaiting ? '#E8501A' : '#4C3FD1'} size={46} />
                        <View style={{ flex: 1, gap: 4 }}>
                          <T w="extrabold" size={15} numberOfLines={1}>{p.itemName}</T>
                          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                            <T w="bold" size={12.5} color={awaiting ? '#6B4A3A' : colors.lavenderInk}>{money(p.amount)}</T>
                            <Chip label={modeLabel(p.paymentMode)} cash={p.paymentMode === 'COD'} />
                          </View>
                        </View>
                        <Badge
                          label={awaiting ? 'Pick a slot' : delivered ? 'Delivered' : order?.status === 'OUT_FOR_DELIVERY' ? 'On the way' : 'Scheduled'}
                          tone={awaiting ? 'dark' : delivered ? 'green' : 'light'}
                        />
                      </View>
                      <T w="semibold" size={12.5} color={awaiting ? '#6B4A3A' : colors.lavenderInk} style={{ marginTop: 10 }}>
                        {purchaseStatus(p, order)}
                      </T>
                      <Track
                        step={stepOf(p, order)}
                        color={awaiting ? '#E8501A' : delivered ? colors.green : '#4C3FD1'}
                        labelColor={awaiting ? '#6B4A3A' : colors.lavenderInk}
                      />
                    </LinearGradient>
                  </Pressable>
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

const styles = StyleSheet.create({
  content: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 130, gap: 16 },
  top: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  count: {
    position: 'absolute',
    top: -2,
    right: -2,
    minWidth: 20,
    height: 20,
    paddingHorizontal: 5,
    borderRadius: 10,
    backgroundColor: colors.orange,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: colors.bg,
  },
  banner: { borderRadius: 24, padding: 18, overflow: 'hidden' },
  bannerTick: { width: 30, height: 30, borderRadius: 15, backgroundColor: colors.green, alignItems: 'center', justifyContent: 'center' },
  bannerCta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    alignSelf: 'flex-start',
    marginTop: 14,
    backgroundColor: colors.ink,
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  order: { borderRadius: 22, padding: 16 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  skeleton: { height: 150, borderRadius: 22, backgroundColor: '#ECE8E3' },
  empty: { alignItems: 'center', gap: 10, paddingVertical: 30 },
});
