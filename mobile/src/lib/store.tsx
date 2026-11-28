import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { api, ApiError, AppNotification, Hold, Order, PaymentChoice, Purchase, Slot } from './api';
import { config } from './config';
import { showLocalNotification } from './notifications';

export type Role = 'customer' | 'admin';

export type User = { email: string; name: string; customerId: string; role: Role };

export type Place = { latitude: number; longitude: number; title: string; subtitle: string; details: string };

/** One booking in progress, for one purchase. */
type Flow = {
  purchase: Purchase | null;
  place: Place | null;
  date: string | null;
  slot: Slot | null;
  hold: Hold | null;
  locked: boolean;
  paymentChoice: PaymentChoice | null;
  result: Order | null;
};

const emptyFlow: Flow = {
  purchase: null,
  place: null,
  date: null,
  slot: null,
  hold: null,
  locked: false,
  paymentChoice: null,
  result: null,
};

type Store = {
  user: User | null;
  login: (u: User) => void;
  logout: () => void;
  flow: Flow;
  updateFlow: (patch: Partial<Flow>) => void;
  startFlow: (purchase: Purchase) => void;
  resetFlow: () => void;
  purchases: Purchase[];
  orders: Order[];
  loading: boolean;
  error: string;
  refresh: () => Promise<void>;
  inbox: AppNotification[];
  unread: number;
  markAllRead: () => Promise<void>;
};

const StoreContext = createContext<Store | null>(null);

/**
 * Everything here belongs to the logged-in user and comes from the backend; logout drops all of it.
 * Responses that arrive after the user changed are ignored, so one account never shows another's data.
 */
export function StoreProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [flow, setFlow] = useState<Flow>(emptyFlow);
  const [purchases, setPurchases] = useState<Purchase[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [inbox, setInbox] = useState<AppNotification[]>([]);
  const session = useRef(0);
  const lastSeen = useRef<string | null>(null);

  const refresh = useCallback(async () => {
    if (!user || user.role !== 'customer') return;
    const mine = session.current;
    setLoading(true);
    setError('');
    try {
      const [p, o] = await Promise.all([api.purchases(user.customerId), api.orders(user.customerId)]);
      if (mine !== session.current) return;
      setPurchases(p);
      setOrders(o);
    } catch (e) {
      if (mine === session.current) setError(e instanceof ApiError ? e.message : 'Could not load your orders.');
    } finally {
      if (mine === session.current) setLoading(false);
    }
  }, [user]);

  const poll = useCallback(async () => {
    if (!user || user.role !== 'customer') return;
    const mine = session.current;
    const firstLook = lastSeen.current === null;
    try {
      const fresh = await api.notifications(user.customerId, lastSeen.current);
      if (mine !== session.current || fresh.length === 0) return;
      // Compared as times, not strings: "…32Z" and "…32.080Z" do not sort correctly as text.
      lastSeen.current = fresh.reduce(
        (max, n) => (max === null || Date.parse(n.createdAt) > Date.parse(max) ? n.createdAt : max),
        lastSeen.current,
      );
      setInbox((list) => {
        const known = new Set(list.map((n) => n.id));
        return [...fresh.filter((n) => !known.has(n.id)), ...list].sort(
          (a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt),
        );
      });
      if (!firstLook) {
        // Older notifications were already seen on another visit; only new ones pop up.
        for (const n of fresh) await showLocalNotification(n.title, n.body);
        refresh();
      }
    } catch {
      // Try again on the next tick.
    }
  }, [user, refresh]);

  useEffect(() => {
    if (!user || user.role !== 'customer') return;
    refresh();
    poll();
    let timer: ReturnType<typeof setInterval> | null = setInterval(poll, config.notificationPollMs);
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        poll();
        refresh();
        if (!timer) timer = setInterval(poll, config.notificationPollMs);
      } else if (timer) {
        clearInterval(timer);
        timer = null;
      }
    });
    return () => {
      if (timer) clearInterval(timer);
      sub.remove();
    };
  }, [user, refresh, poll]);

  const markAllRead = useCallback(async () => {
    const unread = inbox.filter((n) => !n.read);
    if (unread.length === 0) return;
    setInbox((list) => list.map((n) => ({ ...n, read: true })));
    await Promise.all(unread.map((n) => api.markRead(n.id).catch(() => undefined)));
  }, [inbox]);

  const logout = useCallback(() => {
    session.current += 1;
    lastSeen.current = null;
    setUser(null);
    setFlow(emptyFlow);
    setPurchases([]);
    setOrders([]);
    setInbox([]);
    setError('');
    setLoading(false);
  }, []);

  const login = useCallback(
    (u: User) => {
      logout();
      setUser(u);
    },
    [logout],
  );

  const value = useMemo<Store>(
    () => ({
      user,
      login,
      logout,
      flow,
      updateFlow: (patch) => setFlow((f) => ({ ...f, ...patch })),
      startFlow: (purchase) => setFlow((f) => ({ ...emptyFlow, place: f.place, purchase })),
      resetFlow: () => setFlow((f) => ({ ...emptyFlow, place: f.place })),
      purchases,
      orders,
      loading,
      error,
      refresh,
      inbox,
      unread: inbox.filter((n) => !n.read).length,
      markAllRead,
    }),
    [user, login, logout, flow, purchases, orders, loading, error, refresh, inbox, markAllRead],
  );

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

export function useStore() {
  const ctx = useContext(StoreContext);
  if (!ctx) throw new Error('useStore must be used inside StoreProvider');
  return ctx;
}
