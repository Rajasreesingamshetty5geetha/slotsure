import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Parcel, Pin, Van } from '../components/art';
import { Bob, Drive, Road } from '../components/motion';
import { PrimaryButton, T } from '../components/ui';
import { colors, gradients } from '../lib/theme';

export default function Welcome() {
  return (
    <View style={styles.root}>
      <LinearGradient colors={gradients.brand} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.hero}>
        <SafeAreaView edges={['top']} style={{ alignItems: 'center' }}>
          <Animated.View entering={FadeInDown.duration(600)}>
            <T w="extrabold" size={34} style={{ letterSpacing: -0.8, marginTop: 24 }}>SlotSure</T>
          </Animated.View>
          <Animated.View entering={FadeInDown.duration(600).delay(150)}>
            <T w="bold" size={12} color={colors.brown} style={{ letterSpacing: 7, marginTop: 6 }}>ON YOUR SCHEDULE</T>
          </Animated.View>
        </SafeAreaView>
        <Bob style={{ position: 'absolute', left: 46, top: '38%' }} distance={12} duration={1200}>
          <Pin size={44} color="#E11D48" />
        </Bob>
        <Bob style={{ position: 'absolute', right: 40, top: '34%' }} duration={1500}>
          <Parcel size={52} />
        </Bob>
        <Bob style={{ position: 'absolute', left: 74, top: '52%' }} duration={1800} delay={400}>
          <Parcel size={36} />
        </Bob>
        <Drive style={styles.van} duration={2000}>
          <Bob duration={250} distance={2}>
            <Van width={250} />
          </Bob>
        </Drive>
        <View style={styles.road}>
          <Road />
        </View>
      </LinearGradient>

      <Animated.View entering={FadeInDown.duration(700).delay(300)} style={styles.sheet}>
        <SafeAreaView edges={['bottom']} style={{ gap: 14 }}>
          <T w="extrabold" size={25} style={styles.center}>Your parcel, at the hour you choose</T>
          <T size={14.5} color="#525252" style={[styles.center, { lineHeight: 22 }]}>
            Pick a delivery slot that's never overbooked. Your money waits in escrow until it arrives.
          </T>
          <PrimaryButton label="Get started" onPress={() => router.replace('/login')} style={{ marginTop: 6 }} />
        </SafeAreaView>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#fff' },
  hero: { flex: 1, overflow: 'hidden' },
  van: { position: 'absolute', left: 0, right: 0, bottom: 64, alignItems: 'center' },
  road: { position: 'absolute', left: 0, bottom: 52, overflow: 'hidden', width: '100%' },
  sheet: {
    marginTop: -32,
    backgroundColor: '#fff',
    borderTopLeftRadius: 32,
    borderTopRightRadius: 32,
    paddingHorizontal: 24,
    paddingTop: 30,
    paddingBottom: 16,
  },
  center: { textAlign: 'center' },
});
