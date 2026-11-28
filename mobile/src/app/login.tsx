import { LinearGradient } from 'expo-linear-gradient';
import * as Crypto from 'expo-crypto';
import { router } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Parcel, Van } from '../components/art';
import { Bob, Drive } from '../components/motion';
import { Notice, PrimaryButton, T } from '../components/ui';
import { ADMIN_EMAIL, DEMO_USERS } from '../lib/config';
import { askNotificationPermission } from '../lib/notifications';
import { useStore } from '../lib/store';
import { colors, font, gradients } from '../lib/theme';

const DEMO_CHIPS = [...Object.keys(DEMO_USERS), ADMIN_EMAIL];

export default function Login() {
  const { login } = useStore();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const fill = (demo: string) => {
    setEmail(demo);
    setPassword('demo1234');
    setError('');
  };

  const submit = () => {
    const clean = email.trim().toLowerCase();
    if (!/^\S+@\S+\.\S+$/.test(clean) || !password) {
      setError('Enter a valid email and password.');
      return;
    }
    if (clean === ADMIN_EMAIL) {
      login({ email: clean, name: 'Admin', customerId: '', role: 'admin' });
      router.replace('/admin');
      return;
    }
    const name = clean.split('@')[0].replace(/[^a-z]/g, ' ').trim() || 'there';
    login({
      email: clean,
      name: name.charAt(0).toUpperCase() + name.slice(1),
      customerId: DEMO_USERS[clean] ?? Crypto.randomUUID(),
      role: 'customer',
    });
    router.replace('/home');
    askNotificationPermission();
  };

  return (
    <KeyboardAvoidingView style={{ flex: 1, backgroundColor: '#fff' }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView bounces={false} keyboardShouldPersistTaps="handled" contentContainerStyle={{ flexGrow: 1 }}>
        <LinearGradient colors={gradients.brand} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.hero}>
          <SafeAreaView edges={['top']} style={{ paddingHorizontal: 24 }}>
            <Animated.View entering={FadeInDown.duration(500)}>
              <T w="extrabold" size={28} style={{ marginTop: 20, letterSpacing: -0.6 }}>Welcome back</T>
              <T size={14.5} color={colors.brown} style={{ marginTop: 6 }}>Log in to schedule your delivery</T>
            </Animated.View>
          </SafeAreaView>
          <Drive style={{ position: 'absolute', right: -10, bottom: 40 }}>
            <Bob duration={250} distance={2}>
              <Van width={190} />
            </Bob>
          </Drive>
          <Bob style={{ position: 'absolute', left: 28, bottom: 72 }}>
            <Parcel size={40} />
          </Bob>
        </LinearGradient>

        <Animated.View entering={FadeInDown.duration(600).delay(150)} style={styles.sheet}>
          <View style={{ gap: 7 }}>
            <T w="bold" size={13}>Email</T>
            <TextInput
              value={email}
              onChangeText={setEmail}
              placeholder="you@example.com"
              placeholderTextColor={colors.muted}
              autoCapitalize="none"
              keyboardType="email-address"
              autoComplete="email"
              style={styles.input}
            />
          </View>
          <View style={{ gap: 7 }}>
            <T w="bold" size={13}>Password</T>
            <TextInput
              value={password}
              onChangeText={setPassword}
              placeholder="Enter password"
              placeholderTextColor={colors.muted}
              secureTextEntry
              style={styles.input}
            />
          </View>
          {error ? <Notice message={error} /> : null}
          <PrimaryButton label="Log in" onPress={submit} />

          <View style={styles.demo}>
            <T w="extrabold" size={13.5}>Try a demo account</T>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {DEMO_CHIPS.map((demo) => (
                <Pressable key={demo} accessibilityRole="button" onPress={() => fill(demo)} style={[styles.chip, email === demo && styles.chipOn]}>
                  <T w="semibold" size={12.5} color="#9A3412">{demo}</T>
                </Pressable>
              ))}
            </View>
          </View>
        </Animated.View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  hero: { height: 290, overflow: 'hidden' },
  sheet: {
    flex: 1,
    marginTop: -32,
    backgroundColor: '#fff',
    borderTopLeftRadius: 32,
    borderTopRightRadius: 32,
    padding: 24,
    gap: 14,
  },
  input: {
    height: 54,
    borderWidth: 1.5,
    borderColor: colors.line,
    borderRadius: 16,
    paddingHorizontal: 18,
    backgroundColor: '#FFFBF8',
    fontFamily: font.medium,
    fontSize: 15,
    color: colors.text,
  },
  demo: { marginTop: 'auto', backgroundColor: '#FFF4EC', borderRadius: 20, padding: 16, gap: 10 },
  chip: { borderWidth: 1, borderColor: '#FFD9C2', backgroundColor: '#fff', borderRadius: 999, paddingHorizontal: 12, paddingVertical: 9 },
  chipOn: { borderColor: colors.orange, backgroundColor: '#FFF1E7' },
});
