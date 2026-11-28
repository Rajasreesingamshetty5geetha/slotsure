import { Redirect } from 'expo-router';
import { useEffect } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { IconName } from '../components/art';
import { Bob } from '../components/motion';
import { Card, Header, IconCircle, T } from '../components/ui';
import type { NotificationType } from '../lib/api';
import { ago } from '../lib/format';
import { useStore } from '../lib/store';
import { colors } from '../lib/theme';

const LOOK: Record<NotificationType, { icon: IconName; bg: string; color: string }> = {
  SLOT_CONFIRMED: { icon: 'calendar', bg: colors.peach, color: '#E8501A' },
  AGENT_ASSIGNED: { icon: 'truck', bg: colors.lavender, color: '#3730A3' },
  DELIVERED: { icon: 'check', bg: colors.greenBg, color: colors.greenText },
  PAYMENT_FAILED: { icon: 'alert', bg: colors.redBg, color: colors.red },
};

export default function Inbox() {
  const { user, inbox, markAllRead } = useStore();

  // Opening the inbox marks everything in it as read, including items that arrive while it is open.
  const hasUnread = inbox.some((n) => !n.read);
  useEffect(() => {
    if (hasUnread) markAllRead();
  }, [hasUnread, markAllRead]);

  if (!user) return <Redirect href="/login" />;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <SafeAreaView edges={['top']} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.content}>
          <Header title="Notifications" />
          {inbox.length === 0 ? (
            <View style={styles.empty}>
              <Bob duration={1500}>
                <IconCircle name="bell" size={72} />
              </Bob>
              <T w="extrabold" size={17}>All quiet</T>
              <T size={14} color={colors.muted} style={{ textAlign: 'center' }}>Updates about your deliveries will show up here.</T>
            </View>
          ) : (
            inbox.map((n, i) => {
              const look = LOOK[n.type] ?? LOOK.SLOT_CONFIRMED;
              return (
                <Animated.View key={n.id} entering={FadeInDown.duration(350).delay(Math.min(i, 8) * 40)}>
                  <Card style={styles.item}>
                    <IconCircle name={look.icon} bg={look.bg} color={look.color} size={42} />
                    <View style={{ flex: 1, gap: 3 }}>
                      <T w="extrabold" size={14}>{n.title}</T>
                      <T size={13} color={colors.muted} style={{ lineHeight: 19 }}>{n.body}</T>
                      <T w="semibold" size={11.5} color={colors.muted}>{ago(n.createdAt)}</T>
                    </View>
                    {!n.read ? <View style={styles.dot} /> : null}
                  </Card>
                </Animated.View>
              );
            })
          )}
        </ScrollView>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 40, gap: 12 },
  empty: { alignItems: 'center', gap: 10, marginTop: 80, paddingHorizontal: 30 },
  item: { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  dot: { width: 10, height: 10, borderRadius: 5, backgroundColor: colors.orange, marginTop: 4 },
});
