import type { Order, PaymentChoice, PaymentMode, Purchase } from './api';

const rupee = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });

export function money(amount: number) {
  return rupee.format(Number(amount));
}

export function isoDate(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function addDays(d: Date, days: number) {
  const copy = new Date(d);
  copy.setDate(copy.getDate() + days);
  return copy;
}

export function hourLabel(h: number) {
  const suffix = h >= 12 && h < 24 ? 'PM' : 'AM';
  const hour = h % 12 === 0 ? 12 : h % 12;
  return `${hour} ${suffix}`;
}

export function windowLabel(start: number, end: number) {
  const a = hourLabel(start);
  const b = hourLabel(end);
  return a.slice(-2) === b.slice(-2) ? `${a.slice(0, -3)} – ${b}` : `${a} – ${b}`;
}

export function dayLabel(date: string) {
  const d = new Date(`${date}T00:00:00`);
  return d.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });
}

export function timeLabel(iso: string) {
  return new Date(iso).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
}

export function ago(iso: string) {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return dayLabel(isoDate(new Date(iso)));
}

export function countdown(totalSeconds: number) {
  const s = Math.max(0, totalSeconds);
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

export function greeting() {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

export function modeLabel(mode: PaymentMode | null) {
  return mode === 'COD' ? 'Cash on delivery' : 'Prepaid';
}

export function choiceLabel(choice: PaymentChoice | null) {
  switch (choice) {
    case 'ONLINE':
      return 'Paid online · in escrow';
    case 'CASH':
      return 'Cash or UPI on delivery';
    default:
      return 'Prepaid · in escrow';
  }
}

/** "Sun 29 Nov · 6 – 7 PM" for an order with a slot. */
export function slotLabel(order: Pick<Order, 'slotDate' | 'startHour' | 'endHour'>) {
  if (!order.slotDate || order.startHour == null) return 'Slot pending';
  return `${dayLabel(order.slotDate)} · ${windowLabel(order.startHour, order.endHour ?? order.startHour + 1)}`;
}

/** One line describing where a purchase is, using its latest order when there is one. */
export function purchaseStatus(purchase: Purchase, order: Order | undefined) {
  if (purchase.status === 'DELIVERED') return 'Delivered';
  if (purchase.status === 'AWAITING_SLOT') return order?.status === 'PENDING' ? 'Confirming your slot…' : 'Awaiting slot';
  if (order?.status === 'OUT_FOR_DELIVERY') return `Out for delivery${order.agentName ? ` · ${order.agentName}` : ''}`;
  return order ? `Scheduled · ${slotLabel(order)}` : 'Scheduled';
}

export function telLink(phone: string) {
  return `tel:${phone.replace(/[^\d+]/g, '')}`;
}
