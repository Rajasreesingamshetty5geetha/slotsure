import { ExpoConfig } from 'expo/config';

const config: ExpoConfig = {
  name: 'SlotSure',
  slug: 'slotsure',
  version: '1.0.0',
  orientation: 'portrait',
  icon: './assets/images/icon.png',
  scheme: 'slotsure',
  userInterfaceStyle: 'light',
  android: {
    adaptiveIcon: {
      backgroundColor: '#FF8F3F',
      foregroundImage: './assets/images/android-icon-foreground.png',
    },
    config: {
      googleMaps: { apiKey: process.env.EXPO_PUBLIC_GOOGLE_MAPS_KEY },
    },
  },
  plugins: [
    'expo-router',
    ['expo-splash-screen', { backgroundColor: '#FF8F3F', image: './assets/images/splash-icon.png', imageWidth: 76 }],
    ['expo-location', { locationWhenInUsePermission: 'SlotSure uses your location to set the delivery address.' }],
    ['expo-notifications', { color: '#FF6A2B' }],
  ],
  experiments: { reactCompiler: true },
};

export default config;
