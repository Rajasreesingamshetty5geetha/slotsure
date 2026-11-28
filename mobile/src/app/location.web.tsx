import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';
import { PrimaryButton, T } from '../components/ui';
import { colors } from '../lib/theme';

export default function DeliverToWeb() {
  return (
    <View style={styles.root}>
      <T w="extrabold" size={22}>Open SlotSure on your phone</T>
      <T size={14.5} color={colors.muted} style={{ textAlign: 'center' }}>
        Picking a delivery location uses the native map, which runs in the Android or iOS app.
      </T>
      <PrimaryButton label="Back to home" onPress={() => router.replace('/home')} style={{ alignSelf: 'stretch' }} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 14, padding: 24, backgroundColor: colors.bg },
});
