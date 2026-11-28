import { Redirect, Stack } from 'expo-router';
import { useStore } from '../../lib/store';
import { colors } from '../../lib/theme';

/** The admin area has its own stack and no customer tabs. */
export default function AdminLayout() {
  const { user } = useStore();
  if (!user) return <Redirect href="/login" />;
  if (user.role !== 'admin') return <Redirect href="/home" />;
  return <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.bg } }} />;
}
