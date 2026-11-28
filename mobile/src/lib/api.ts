import * as Location from 'expo-location';
import { config } from './config';

export type Slot = {
  slotId: string;
  h3Cell: string;
  areaName: string;
  date: string;
  startHour: number;
  endHour: number;
  capacity: number;
  remaining: number;
  priceTag: 'GREEN' | 'NORMAL' | 'BUSY';
};

export type Hold = { holdId: string; slotId: string; expiresAt: string };

export type PaymentMode = 'PREPAID' | 'COD';
export type PaymentChoice = 'PREPAID' | 'ONLINE' | 'CASH';
export type PurchaseStatus = 'AWAITING_SLOT' | 'SCHEDULED' | 'DELIVERED';
export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'FAILED' | 'CANCELLED';

export type Purchase = {
  purchaseId: string;
  customerId: string;
  itemName: string;
  category: string;
  amount: number;
  paymentMode: PaymentMode;
  placedAt: string;
  status: PurchaseStatus;
  orderId: string | null;
};

export type Order = {
  orderId: string;
  purchaseId: string | null;
  itemName: string | null;
  amount: number;
  paymentMode: PaymentMode | null;
  paymentChoice: PaymentChoice | null;
  status: OrderStatus;
  slotDate: string | null;
  startHour: number | null;
  endHour: number | null;
  address: string | null;
  areaName: string | null;
  agentName: string | null;
  agentPhone: string | null;
  deliveredAt: string | null;
  createdAt: string;
  sagaStep?: string | null;
  updatedAt?: string;
};

export type CreateOrder = {
  customerId: string;
  purchaseId: string;
  holdId: string;
  paymentChoice: PaymentChoice;
  lat: number;
  lng: number;
  address: string;
  areaName: string;
  slotDate: string;
  startHour: number;
  endHour: number;
};

export type NotificationType = 'SLOT_CONFIRMED' | 'AGENT_ASSIGNED' | 'DELIVERED' | 'PAYMENT_FAILED';

export type AppNotification = {
  id: string;
  orderId: string;
  type: NotificationType;
  title: string;
  body: string;
  createdAt: string;
  read: boolean;
};

export type AdminSummary = {
  date: string;
  totalBookings: number;
  confirmed: number;
  failed: number;
  delivered: number;
  prepaidAmount: number;
  onlineAmount: number;
  codExpected: number;
  codCollected: number;
  topItems: { itemName: string; count: number }[];
  byHour: { startHour: number; count: number }[];
  byArea: { areaName: string; count: number }[];
};

export type AdminSlots = {
  date: string;
  totalCapacity: number;
  totalBooked: number;
  utilisationPct: number;
  byArea: { areaName: string; capacity: number; booked: number }[];
  busiest: { areaName: string; startHour: number; endHour: number; booked: number; capacity: number }[];
};

export type AdminDispatch = {
  date: string;
  agentsTotal: number;
  agentsActive: number;
  assigned: number;
  delivered: number;
  byArea: { areaName: string; agents: number; deliveries: number }[];
};

export type Agent = {
  agentId: string;
  name: string;
  phone: string;
  homeArea: string;
  assigned: number;
  delivered: number;
  active?: boolean;
};

export type AdminPayments = {
  date: string;
  prepaidCaptured: number;
  escrowHeld: number;
  escrowReleased: number;
  codCollected: number;
  refunds: number;
  ledgerDifference: number;
};

export type LedgerCheck = { totalDebit: number; totalCredit: number; difference: number };

export type PlaceResult = { title: string; subtitle: string; latitude: number; longitude: number };

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
  }
}

async function request<T>(url: string, init: RequestInit & { idempotencyKey?: string } = {}): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 8000);
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (init.body) headers['Content-Type'] = 'application/json';
  if (init.idempotencyKey) headers['Idempotency-Key'] = init.idempotencyKey;

  let res: Response;
  try {
    res = await fetch(url, { ...init, headers, signal: controller.signal });
  } catch {
    throw new ApiError(0, 'NETWORK', `Can't reach the server at ${config.host}. Check the backend is running and your phone is on the same Wi-Fi.`);
  } finally {
    clearTimeout(timer);
  }

  const text = await res.text();
  let data: any = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!res.ok) {
    throw new ApiError(res.status, data?.code ?? 'ERROR', data?.message ?? `Request failed (${res.status})`);
  }
  return data as T;
}

const q = encodeURIComponent;

export const api = {
  slots(lat: number, lng: number, date: string) {
    return request<Slot[]>(`${config.slotApi}/api/slots?lat=${lat}&lng=${lng}&date=${date}`);
  },

  hold(slotId: string, customerId: string, idempotencyKey: string) {
    return request<Hold>(`${config.slotApi}/api/holds`, {
      method: 'POST',
      body: JSON.stringify({ slotId, customerId }),
      idempotencyKey,
    });
  },

  purchases(customerId: string) {
    return request<Purchase[]>(`${config.orderApi}/api/purchases?customerId=${q(customerId)}`);
  },

  createOrder(body: CreateOrder, idempotencyKey: string) {
    return request<{ orderId: string; status: OrderStatus; paymentMode: PaymentMode; paymentChoice: PaymentChoice }>(
      `${config.orderApi}/api/orders`,
      { method: 'POST', body: JSON.stringify(body), idempotencyKey },
    );
  },

  orders(customerId: string) {
    return request<Order[]>(`${config.orderApi}/api/orders?customerId=${q(customerId)}`);
  },

  order(orderId: string) {
    return request<Order>(`${config.orderApi}/api/orders/${orderId}`);
  },

  notifications(customerId: string, after?: string | null) {
    const since = after ? `&after=${q(after)}` : '';
    return request<AppNotification[]>(`${config.orderApi}/api/notifications?customerId=${q(customerId)}${since}`);
  },

  markRead(id: string) {
    return request<null>(`${config.orderApi}/api/notifications/${id}/read`, { method: 'POST' });
  },

  ledgerCheck() {
    return request<LedgerCheck>(`${config.paymentApi}/api/ledger/check`);
  },

  admin: {
    summary: (date: string) => request<AdminSummary>(`${config.orderApi}/api/admin/summary?date=${date}`),
    orders: (date: string) => request<Order[]>(`${config.orderApi}/api/admin/orders?date=${date}`),
    slots: (date: string) => request<AdminSlots>(`${config.slotApi}/api/admin/slots?date=${date}`),
    dispatch: (date: string) => request<AdminDispatch>(`${config.dispatchApi}/api/admin/dispatch?date=${date}`),
    agents: (date: string) => request<Agent[]>(`${config.dispatchApi}/api/agents?date=${date}`),
    payments: (date: string) => request<AdminPayments>(`${config.paymentApi}/api/admin/payments?date=${date}`),
    complete: (orderId: string, idempotencyKey: string) =>
      request<{ orderId: string; deliveredAt: string }>(`${config.dispatchApi}/api/deliveries/${orderId}/complete`, {
        method: 'POST',
        idempotencyKey,
      }),
  },
};

export async function searchPlaces(query: string): Promise<PlaceResult[]> {
  if (config.mapsKey) {
    const res = await fetch('https://places.googleapis.com/v1/places:searchText', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Goog-Api-Key': config.mapsKey,
        'X-Goog-FieldMask': 'places.displayName,places.formattedAddress,places.location',
      },
      body: JSON.stringify({
        textQuery: query,
        regionCode: 'IN',
        pageSize: 5,
        locationBias: { circle: { center: config.cityCenter, radius: 30000 } },
      }),
    });
    if (res.ok) {
      const data = await res.json();
      return (data.places ?? []).map((p: any) => ({
        title: p.displayName?.text ?? query,
        subtitle: p.formattedAddress ?? '',
        latitude: p.location.latitude,
        longitude: p.location.longitude,
      }));
    }
  }
  const found = await Location.geocodeAsync(`${query}, Bengaluru`);
  return found.slice(0, 5).map((p) => ({ title: query, subtitle: 'Bengaluru', latitude: p.latitude, longitude: p.longitude }));
}

type Address = { title: string; subtitle: string };

/** Google's formatted address when a Maps key is set; otherwise the device geocoder. */
export async function describePoint(latitude: number, longitude: number): Promise<Address> {
  if (config.mapsKey) {
    try {
      const google = await googleAddress(latitude, longitude);
      if (google) return google;
    } catch {
      // Fall through to the device geocoder.
    }
  }
  try {
    const [a] = await Location.reverseGeocodeAsync({ latitude, longitude });
    if (!a) throw new Error('no address');
    if (a.formattedAddress) {
      const [first, ...rest] = a.formattedAddress.split(', ');
      return { title: first, subtitle: rest.join(', ') || a.formattedAddress };
    }
    const title = a.name && !/^\d+$/.test(a.name) ? a.name : a.street ?? a.district ?? 'Pinned location';
    const subtitle = [a.street !== title ? a.street : null, a.district, a.city, a.postalCode].filter(Boolean).join(', ');
    return { title, subtitle };
  } catch {
    return { title: 'Pinned location', subtitle: `${latitude.toFixed(5)}, ${longitude.toFixed(5)}` };
  }
}

async function googleAddress(latitude: number, longitude: number): Promise<Address | null> {
  const url = `https://maps.googleapis.com/maps/api/geocode/json?latlng=${latitude},${longitude}&key=${config.mapsKey}`;
  const res = await fetch(url);
  if (!res.ok) return null;
  const data = await res.json();
  if (data.status !== 'OK' || !Array.isArray(data.results)) return null;
  const best = data.results.find((r: any) => !(r.types ?? []).includes('plus_code')) ?? data.results[0];
  const formatted: string | undefined = best?.formatted_address;
  if (!formatted) return null;
  const parts = formatted.split(', ');
  return { title: parts[0], subtitle: parts.slice(1).join(', ') || formatted };
}
