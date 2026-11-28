import { LinearGradient } from 'expo-linear-gradient';
import * as Location from 'expo-location';
import { Redirect, router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Keyboard, KeyboardAvoidingView, Platform, Pressable, StyleSheet, TextInput, View } from 'react-native';
import MapView, { Details, PROVIDER_GOOGLE, Region } from 'react-native-maps';
import Animated, { FadeIn, FadeInDown } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Icon, Pin } from '../components/art';
import { Bob, Spin } from '../components/motion';
import { Notice, PrimaryButton, RoundButton, T } from '../components/ui';
import { describePoint, PlaceResult, searchPlaces } from '../lib/api';
import { config } from '../lib/config';
import { useStore } from '../lib/store';
import { colors, font, gradients, shadow } from '../lib/theme';

const START: Region = { ...config.cityCenter, latitudeDelta: 0.08, longitudeDelta: 0.08 };
const CLOSE = { latitudeDelta: 0.004, longitudeDelta: 0.004 };
const STREET_ZOOM = 17;

/** The Pin art is 44 x 56 with its point at y = 54; these place that point on the map centre. */
const PIN_SIZE = 46;
const PIN_HEIGHT = (PIN_SIZE * 56) / 44;
const PIN_TIP = (PIN_HEIGHT * 54) / 56;

export default function DeliverTo() {
  const { user, flow, updateFlow } = useStore();
  const map = useRef<MapView>(null);
  const lookup = useRef(0);
  // Only a drag/pinch or one of our own camera moves should change the address; the first render's region should not.
  const gesture = useRef(false);
  const flying = useRef(false);
  const [center, setCenter] = useState(flow.place ?? config.cityCenter);
  const [address, setAddress] = useState(flow.place ? { title: flow.place.title, subtitle: flow.place.subtitle } : null);
  const [details, setDetails] = useState(flow.place?.details ?? '');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<PlaceResult[]>([]);
  const [searching, setSearching] = useState(false);
  const [locating, setLocating] = useState(false);
  const [moving, setMoving] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    Location.requestForegroundPermissionsAsync().catch(() => undefined);
  }, []);

  if (!user) return <Redirect href="/login" />;
  if (!flow.purchase) return <Redirect href="/home" />;

  const locked = flow.locked;
  const initialRegion: Region = flow.place ? { latitude: flow.place.latitude, longitude: flow.place.longitude, ...CLOSE } : START;

  const fly = (latitude: number, longitude: number) => {
    flying.current = true;
    map.current?.animateCamera({ center: { latitude, longitude }, zoom: STREET_ZOOM, altitude: 700 }, { duration: 700 });
  };

  const onMove = (_: Region, d?: Details) => {
    if (d?.isGesture) gesture.current = true;
    if (!moving) setMoving(true);
  };

  const onSettled = async (r: Region, d?: Details) => {
    const fromUser = gesture.current || !!d?.isGesture;
    const fromUs = flying.current;
    gesture.current = false;
    flying.current = false;
    setMoving(false);
    if (!fromUser && !fromUs) return;
    setCenter({ latitude: r.latitude, longitude: r.longitude });
    const id = ++lookup.current;
    const found = await describePoint(r.latitude, r.longitude);
    if (id === lookup.current) setAddress(found);
  };

  const useMyLocation = async () => {
    setMessage('');
    setLocating(true);
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        setMessage('Location permission denied. Search your address instead.');
        return;
      }
      // The last fix comes back at once; the fresh high-accuracy one then corrects it.
      const last = await Location.getLastKnownPositionAsync({ maxAge: 5 * 60 * 1000, requiredAccuracy: 200 });
      if (last) fly(last.coords.latitude, last.coords.longitude);
      const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
      fly(pos.coords.latitude, pos.coords.longitude);
    } catch {
      setMessage("Couldn't get your location. Try searching instead.");
    } finally {
      setLocating(false);
    }
  };

  const search = async () => {
    if (query.trim().length < 3) return;
    Keyboard.dismiss();
    setSearching(true);
    setMessage('');
    try {
      const found = await searchPlaces(query.trim());
      setResults(found);
      if (found.length === 0) setMessage('No places found. Try a nearby landmark.');
    } catch {
      setMessage('Search failed. Check your internet connection.');
    } finally {
      setSearching(false);
    }
  };

  const pick = (p: PlaceResult) => {
    setResults([]);
    setQuery('');
    fly(p.latitude, p.longitude);
  };

  const confirm = () => {
    if (!address) return;
    updateFlow({
      place: { ...center, title: address.title, subtitle: address.subtitle, details: details.trim() },
      slot: null,
      hold: null,
    });
    router.push('/slots');
  };

  return (
    <KeyboardAvoidingView style={{ flex: 1, backgroundColor: '#EEF0F3' }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <View style={styles.mapArea} onTouchStart={() => (gesture.current = true)}>
        <MapView
          ref={map}
          style={StyleSheet.absoluteFill}
          provider={Platform.OS === 'android' ? PROVIDER_GOOGLE : undefined}
          initialRegion={initialRegion}
          showsUserLocation
          showsMyLocationButton={false}
          toolbarEnabled={false}
          showsPointsOfInterests={false}
          scrollEnabled={!locked}
          zoomEnabled={!locked}
          onPanDrag={() => (gesture.current = true)}
          onRegionChange={onMove}
          onRegionChangeComplete={onSettled}
        />

        {!moving && address ? (
          <Animated.View entering={FadeIn.duration(250)} pointerEvents="none" style={styles.tipRow}>
            <View style={styles.tip}>
              <T w="bold" size={12.5} color="#fff">Your order will be delivered here</T>
            </View>
          </Animated.View>
        ) : null}
        <View pointerEvents="none" style={styles.pinShadow} />
        <View pointerEvents="none" style={styles.pin}>
          {moving ? (
            <View style={{ transform: [{ translateY: -12 }] }}>
              <Pin size={PIN_SIZE} />
            </View>
          ) : (
            <Bob distance={5} duration={700}>
              <Pin size={PIN_SIZE} />
            </Bob>
          )}
        </View>

        <SafeAreaView edges={['top']} style={styles.top} pointerEvents="box-none">
          <View style={{ flexDirection: 'row', gap: 10 }}>
            <RoundButton icon="back" label="Back" onPress={() => router.back()} />
            <View style={styles.search}>
              <Icon name="search" size={18} color={colors.muted} stroke={2.2} />
              <TextInput
                value={query}
                onChangeText={setQuery}
                onSubmitEditing={search}
                editable={!locked}
                returnKeyType="search"
                placeholder="Search area, street, landmark"
                placeholderTextColor={colors.muted}
                accessibilityLabel="Search area, street or landmark"
                style={styles.searchInput}
              />
              {searching ? <ActivityIndicator color={colors.orange} /> : null}
            </View>
          </View>
          {results.length > 0 ? (
            <Animated.View entering={FadeInDown.duration(250)} style={styles.results}>
              {results.map((r, i) => (
                <Pressable key={`${r.latitude}-${i}`} accessibilityRole="button" onPress={() => pick(r)} style={[styles.result, i > 0 && styles.resultLine]}>
                  <Icon name="pin" size={18} color="#E8501A" />
                  <View style={{ flex: 1 }}>
                    <T w="bold" size={14} numberOfLines={1}>{r.title}</T>
                    <T size={12} color={colors.muted} numberOfLines={1}>{r.subtitle}</T>
                  </View>
                </Pressable>
              ))}
            </Animated.View>
          ) : null}
        </SafeAreaView>

        <Pressable accessibilityRole="button" onPress={useMyLocation} disabled={locked || locating} style={styles.locate}>
          {locating ? (
            <Spin>
              <Icon name="locate" size={18} color="#E8501A" stroke={2.2} />
            </Spin>
          ) : (
            <Icon name="locate" size={18} color="#E8501A" stroke={2.2} />
          )}
          <T w="bold" size={13.5} color="#9A3412">Use current location</T>
        </Pressable>
      </View>

      <Animated.View entering={FadeInDown.duration(500)} style={styles.sheet}>
        <SafeAreaView edges={['bottom']} style={{ gap: 14 }}>
          <View style={styles.handle} />
          {locked ? <Notice message="Location is locked while a booking is in progress." /> : null}
          {message ? <Notice message={message} /> : null}
          <LinearGradient colors={gradients.peach} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.address}>
            <LinearGradient colors={gradients.brand} style={styles.addressIcon}>
              <Icon name="pin" size={20} stroke={2.2} />
            </LinearGradient>
            <View style={{ flex: 1 }}>
              <T w="extrabold" size={16} numberOfLines={1}>{moving ? 'Locating…' : address?.title ?? 'Move the map to your door'}</T>
              <T size={12.5} color="#6B4A3A" numberOfLines={2}>
                {moving ? ' ' : address?.subtitle ?? 'Drag the map so the pin sits on your building, or use your current location'}
              </T>
            </View>
          </LinearGradient>
          <TextInput
            value={details}
            onChangeText={setDetails}
            editable={!locked}
            placeholder="Flat, floor, landmark (optional)"
            placeholderTextColor={colors.muted}
            style={styles.details}
          />
          <PrimaryButton label="Confirm location" onPress={confirm} disabled={!address || moving || locked} />
        </SafeAreaView>
      </Animated.View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  mapArea: { flex: 1, backgroundColor: '#EEF0F3' },
  // The pin's point sits exactly on the centre of the map area, which is the map region's centre.
  pin: { position: 'absolute', top: '50%', left: '50%', marginTop: -PIN_TIP, marginLeft: -PIN_SIZE / 2 },
  pinShadow: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    marginTop: -3,
    marginLeft: -8,
    width: 16,
    height: 6,
    borderRadius: 8,
    backgroundColor: 'rgba(20,20,20,0.25)',
  },
  tipRow: { position: 'absolute', left: 0, right: 0, top: '50%', marginTop: -PIN_TIP - 46, alignItems: 'center' },
  tip: { backgroundColor: colors.ink, paddingHorizontal: 12, paddingVertical: 8, borderRadius: 12 },
  top: { position: 'absolute', left: 16, right: 16, top: 0, paddingTop: 8 },
  search: {
    flex: 1,
    height: 46,
    borderRadius: 999,
    backgroundColor: '#fff',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 16,
    ...shadow,
  },
  searchInput: { flex: 1, fontFamily: font.medium, fontSize: 14.5, color: colors.text, paddingVertical: 0 },
  results: { marginTop: 10, backgroundColor: '#fff', borderRadius: 20, paddingHorizontal: 14, ...shadow },
  result: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 12 },
  resultLine: { borderTopWidth: 1, borderTopColor: colors.line },
  locate: {
    position: 'absolute',
    right: 16,
    bottom: 28,
    height: 44,
    paddingHorizontal: 16,
    borderRadius: 999,
    backgroundColor: '#fff',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    ...shadow,
  },
  sheet: {

    backgroundColor: '#fff',
    borderTopLeftRadius: 32,
    borderTopRightRadius: 32,
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 16,
    ...shadow,
    shadowOffset: { width: 0, height: -8 },
  },
  handle: { width: 44, height: 5, borderRadius: 5, backgroundColor: '#E7E2DC', alignSelf: 'center' },
  address: { borderRadius: 20, padding: 14, flexDirection: 'row', alignItems: 'center', gap: 12 },
  addressIcon: { width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center' },
  details: {
    height: 50,
    borderWidth: 1.5,
    borderColor: colors.line,
    borderRadius: 16,
    paddingHorizontal: 16,
    backgroundColor: '#FFFBF8',
    fontFamily: font.medium,
    fontSize: 14.5,
    color: colors.text,
  },
});
