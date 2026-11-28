import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { Redirect, router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import Animated, { FadeInDown, ZoomIn } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Icon, IconName } from '../components/art';
import { Bob } from '../components/motion';
import { Header, PrimaryButton, T } from '../components/ui';
import type { PaymentChoice } from '../lib/api';
import { dayLabel, money, windowLabel } from '../lib/format';
import { useStore } from '../lib/store';
import { colors, gradients, shadow } from '../lib/theme';

type Option = { choice: Exclude<PaymentChoice, 'PREPAID'>; icon: IconName; title: string; line: string; note: string };

/** For a cash-on-delivery purchase: pay now into escrow, or pay the agent at the door. */
export default function Payment() {
  const { user, flow, updateFlow } = useStore();
  const [choice, setChoice] = useState<Option['choice'] | null>(
    flow.paymentChoice === 'ONLINE' || flow.paymentChoice === 'CASH' ? flow.paymentChoice : null,
  );

  if (!user) return <Redirect href="/login" />;
  const { purchase, slot, date, hold } = flow;
  if (!purchase || !slot || !date || !hold) return <Redirect href="/home" />;

  const amount = money(purchase.amount);
  const options: Option[] = [
    { choice: 'ONLINE', icon: 'card', title: 'Pay now', line: 'UPI / card', note: 'Secured in escrow until delivery' },
    { choice: 'CASH', icon: 'cash', title: 'Cash or UPI on delivery', line: `Pay ${amount} to the agent`, note: 'No payment needed now' },
  ];

  const next = () => {
    if (!choice) return;
    updateFlow({ paymentChoice: choice });
    router.push('/confirm');
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <SafeAreaView edges={['top']} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.content}>
          <Header title="How would you like to pay?" />

          <Animated.View entering={FadeInDown.duration(450)}>
            <LinearGradient colors={gradients.peach} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.summary}>
              <View style={{ flex: 1 }}>
                <T w="extrabold" size={15} numberOfLines={1}>{purchase.itemName}</T>
                <T size={12.5} color="#6B4A3A">{dayLabel(date)} · {windowLabel(slot.startHour, slot.endHour)}</T>
              </View>
              <T w="extrabold" size={20}>{amount}</T>
            </LinearGradient>
          </Animated.View>

          {options.map((o, i) => {
            const on = choice === o.choice;
            return (
              <Animated.View key={o.choice} entering={FadeInDown.duration(450).delay(80 + i * 80)}>
                <Pressable
                  accessibilityRole="radio"
                  accessibilityState={{ selected: on }}
                  accessibilityLabel={`${o.title}, ${o.line}`}
                  onPress={() => {
                    Haptics.selectionAsync();
                    setChoice(o.choice);
                  }}
                  style={({ pressed }) => [styles.option, on && styles.optionOn, { transform: [{ scale: pressed ? 0.98 : 1 }] }]}
                >
                  {on ? <LinearGradient colors={gradients.peach} style={[StyleSheet.absoluteFill, { borderRadius: 22 }]} /> : null}
                  <View style={[styles.optionIcon, { backgroundColor: o.choice === 'ONLINE' ? colors.lavender : '#FEF3C7' }]}>
                    <Icon name={o.icon} size={22} color={o.choice === 'ONLINE' ? '#3730A3' : '#92400E'} stroke={2} />
                  </View>
                  <View style={{ flex: 1, gap: 2 }}>
                    <T w="extrabold" size={15.5}>{o.title}</T>
                    <T w="semibold" size={13}>{o.line}</T>
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 4 }}>
                      <Icon name={o.choice === 'ONLINE' ? 'shield' : 'check'} size={14} color={colors.greenText} stroke={2.2} />
                      <T w="semibold" size={12} color={colors.greenText}>{o.note}</T>
                    </View>
                  </View>
                  <View style={[styles.radio, on && styles.radioOn]}>
                    {on ? (
                      <Animated.View entering={ZoomIn.springify()}>
                        <Icon name="check" size={13} color="#fff" stroke={3} />
                      </Animated.View>
                    ) : null}
                  </View>
                </Pressable>
              </Animated.View>
            );
          })}

          <Animated.View entering={FadeInDown.duration(450).delay(260)}>
            <LinearGradient colors={gradients.lavender} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.escrow}>
              <Bob duration={1500}>
                <Icon name="shield" size={30} color="#3730A3" stroke={1.8} />
              </Bob>
              <T w="semibold" size={12.5} color={colors.lavenderInk} style={{ flex: 1, lineHeight: 19 }}>
                Paying now? Your money waits in escrow and reaches the seller only after delivery.
              </T>
            </LinearGradient>
          </Animated.View>
        </ScrollView>

        <SafeAreaView edges={['bottom']} style={styles.footer}>
          <PrimaryButton label={choice ? 'Continue' : 'Choose how to pay'} onPress={next} disabled={!choice} />
        </SafeAreaView>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 24, gap: 14 },
  summary: { borderRadius: 22, padding: 16, flexDirection: 'row', alignItems: 'center', gap: 12 },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    padding: 16,
    borderRadius: 22,
    backgroundColor: '#fff',
    borderWidth: 2,
    borderColor: 'transparent',
    overflow: 'hidden',
    ...shadow,
    shadowOpacity: 0.05,
  },
  optionOn: { borderColor: colors.ink, shadowColor: '#E8501A', shadowOpacity: 0.2, elevation: 6 },
  optionIcon: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  radio: { width: 24, height: 24, borderRadius: 12, borderWidth: 2, borderColor: colors.line, alignItems: 'center', justifyContent: 'center' },
  radioOn: { backgroundColor: colors.ink, borderColor: colors.ink },
  escrow: { borderRadius: 20, padding: 14, flexDirection: 'row', alignItems: 'center', gap: 12 },
  footer: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 12, backgroundColor: colors.bg },
});
