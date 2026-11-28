import { Redirect, router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Icon } from '../components/art';
import { Card, IconCircle, NavBar, Notice, T } from '../components/ui';
import { api, ApiError, LedgerCheck } from '../lib/api';
import { config } from '../lib/config';
import { money } from '../lib/format';
import { useStore } from '../lib/store';
import { colors } from '../lib/theme';

export default function Profile() {
  const { user, logout } = useStore();
  const [ledger, setLedger] = useState<LedgerCheck | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setLedger(await api.ledgerCheck());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not load the ledger.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (!user) return <Redirect href="/login" />;

  const balanced = ledger && Number(ledger.difference) === 0;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <SafeAreaView edges={['top']} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.content}>
          <T w="extrabold" size={26} style={{ letterSpacing: -0.6 }}>Profile</T>
          <Card style={styles.row}>
            <IconCircle name="user" />
            <View style={{ flex: 1 }}>
              <T w="extrabold" size={16}>{user.name}</T>
              <T size={13} color={colors.muted}>{user.email}</T>
            </View>
          </Card>

          <Card style={{ gap: 14 }}>
            <View style={styles.row}>
              <IconCircle name="shield" bg={colors.lavender} color="#3730A3" />
              <View style={{ flex: 1 }}>
                <T w="extrabold" size={15}>Escrow ledger check</T>
                <T size={12.5} color={colors.muted}>Every debit must have a matching credit</T>
              </View>
              <Pressable accessibilityRole="button" accessibilityLabel="Refresh ledger" onPress={load} style={styles.refresh}>
                {loading ? <ActivityIndicator color={colors.ink} /> : <Icon name="refresh" size={18} />}
              </Pressable>
            </View>
            {error ? <Notice message={error} onRetry={load} /> : null}
            {ledger ? (
              <View style={styles.stats}>
                <Stat label="Debit" value={money(Number(ledger.totalDebit))} />
                <Stat label="Credit" value={money(Number(ledger.totalCredit))} />
                <Stat label="Difference" value={money(Number(ledger.difference))} color={balanced ? colors.greenText : colors.red} />
              </View>
            ) : null}
          </Card>

          <Card style={{ gap: 4 }}>
            <T w="semibold" size={12} color={colors.muted}>Server</T>
            <T w="bold" size={14}>{config.host}</T>
          </Card>

          <Pressable
            accessibilityRole="button"
            onPress={() => {
              logout();
              router.replace('/login');
            }}
            style={styles.logout}
          >
            <Icon name="logout" size={18} color={colors.red} />
            <T w="bold" size={15} color={colors.red}>Log out</T>
          </Pressable>
        </ScrollView>
      </SafeAreaView>
      <NavBar />
    </View>
  );
}

function Stat({ label, value, color = colors.text }: { label: string; value: string; color?: string }) {
  return (
    <View style={{ flex: 1 }}>
      <T w="semibold" size={12} color={colors.muted}>{label}</T>
      <T w="extrabold" size={15} color={color}>{value}</T>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 20, paddingTop: 16, paddingBottom: 130, gap: 14 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  refresh: { width: 44, height: 44, borderRadius: 22, backgroundColor: colors.bg, alignItems: 'center', justifyContent: 'center' },
  stats: { flexDirection: 'row', gap: 10, borderTopWidth: 1, borderTopColor: colors.line, paddingTop: 12 },
  logout: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, height: 52, borderRadius: 999, backgroundColor: '#fff' },
});
