const host = process.env.EXPO_PUBLIC_API_HOST ?? 'localhost';

export const config = {
  host,
  slotApi: `http://${host}:8081`,
  orderApi: `http://${host}:8082`,
  paymentApi: `http://${host}:8083`,
  dispatchApi: `http://${host}:8084`,
  mapsKey: process.env.EXPO_PUBLIC_GOOGLE_MAPS_KEY ?? '',
  cityCenter: { latitude: 12.9716, longitude: 77.5946 },
  notificationPollMs: 15000,
};

export const ADMIN_EMAIL = 'admin@slotsure.demo';

export const DEMO_USERS: Record<string, string> = {
  'user1@slotsure.demo': '6f1c2a10-3b7d-4e2a-9c11-0a1b2c3d4e01',
  'user2@slotsure.demo': '6f1c2a10-3b7d-4e2a-9c11-0a1b2c3d4e02',
  'user3@slotsure.demo': '6f1c2a10-3b7d-4e2a-9c11-0a1b2c3d4e03',
};
