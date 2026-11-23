'use client';

import { useQuery } from '@tanstack/react-query';
import { dataOf, http } from '@/lib/http';

// What the guest's phone remembers about where it sat down, so the payment return page can find its way back.
const KEY = 'plately.table';

export interface RememberedTable {
  slug: string;
  n: string;
  email?: string;
}

export function rememberTable(table: RememberedTable) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(table));
  } catch {
    // Private browsing may refuse; the guest simply scans again.
  }
}

export function rememberedTable(): RememberedTable | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as RememberedTable) : null;
  } catch {
    return null;
  }
}

export function forgetTable() {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // Nothing to do.
  }
}

export interface SeatOnMap {
  seatId: string;
  seatNumber: string;
  position: number;
  status: 'VACANT' | 'HELD' | 'OCCUPIED';
}

export interface SeatMap {
  businessName?: string;
  tableId: string;
  tableNumber: string;
  displayNumber: number;
  // The backend spells this with a capital C.
  Capacity?: number;
  capacity?: number;
  seats: SeatOnMap[];
}

export interface SessionSeat {
  seatId: string;
  seatNumber: string;
  position: number;
  status: 'HELD' | 'OCCUPIED';
  guestName?: string;
  guestPhone?: string;
  hasDevice?: boolean;
  mine?: boolean;
  heldUntil?: string;
}

export interface MySession {
  businessName?: string;
  sessionId: string;
  status: string;
  joinCode: string;
  tableId: string;
  tableNumber: string;
  displayNumber: number;
  seatId: string;
  holdsEnabled: boolean;
  seats: SessionSeat[];
}

export interface SessionOrder {
  orderId: string;
  orderNumber: string;
  paymentReference?: string;
  createdAt?: string;
  status: string;
  items: string[];
  amount?: number;
  forSeatPosition?: number;
  forName?: string;
  forMe: boolean;
}

export interface MySessionOrders {
  orders: SessionOrder[];
  paidByYou: number;
  paidByTable: number;
}

export interface MenuItem {
  itemName: string;
  itemDescription?: string;
  itemPrice: string;
  imageUrl?: string;
  readyInMinutes?: number;
}

export interface MenuSection {
  categoryName: string;
  itemResponse: MenuItem[];
}

export interface Receipt {
  receiptNumber: string;
  paymentReference: string;
  orderId: string;
  tableNumber: string;
  seatLabel: string;
  paymentDate?: string;
  items: Array<{ itemName: string; quantity: number; unitPrice: number; totalPrice: number; specialInstructions?: string }>;
  subtotal: number;
  totalPaid: number;
  status: string;
  refundMessage?: string;
  refundLink?: string;
}

export const guestApi = {
  seatMap: async (slug: string, n: string) => dataOf<SeatMap>(await http.get(`/api/v1/t/${slug}/tables/${n}/seatMap`)),
  receipt: async (reference: string) => dataOf<Receipt>(await http.get(`/api/v1/receipts/${reference}`)),
  // The server answers in plain words, for a sent receipt and for every refusal.
  emailReceipt: async (reference: string, email: string) => dataOf<string>(await http.post(`/api/v1/receipts/${reference}/email`, { email })),
  session: async () => dataOf<MySession>(await http.get('/api/v1/sessions/me')),
  orders: async () => dataOf<MySessionOrders>(await http.get('/api/v1/sessions/me/orders')),
  menu: async (slug: string) => {
    const data = dataOf<{ categoryAndItemResponse?: Array<{ categoryName?: string; CategoryName?: string; itemResponse: Array<Record<string, string>> }> }>(
      await http.get(`/api/v1/t/${slug}/menu`),
    );
    return (data.categoryAndItemResponse ?? []).map<MenuSection>((section) => ({
      categoryName: section.categoryName ?? section.CategoryName ?? '',
      itemResponse: (section.itemResponse ?? []).map((item) => ({
        itemName: item.itemName ?? item.ItemName,
        itemDescription: item.itemDescription ?? item.ItemDescription,
        itemPrice: item.itemPrice ?? item.ItemPrice,
        imageUrl: item.imageUrl || undefined,
        readyInMinutes: item.readyInMinutes ? Number(item.readyInMinutes) : undefined,
      })),
    })).filter((section) => section.itemResponse.length > 0);
  },
};

export function useSession() {
  return useQuery({ queryKey: ['session'], queryFn: guestApi.session, retry: false });
}

export function useSessionOrders() {
  return useQuery({ queryKey: ['orders'], queryFn: guestApi.orders, retry: false });
}

export interface EventPreview {
  eventName: string;
  venue?: string;
  tableNumber: number;
  startsAt?: string;
  endsAt?: string;
  open: boolean;
  over: boolean;
}

export interface EventPlate {
  id: string;
  name: string;
  description?: string;
  available: boolean;
}

export interface MyEvent {
  eventName: string;
  venue?: string;
  endsAt?: string;
  guestName: string;
  platesUsed: number;
  plateLimit?: number | null;
  // The plate this guest is still waiting for, if there is one.
  platePending?: string | null;
  note: string;
  plates: EventPlate[];
}

export const eventGuestApi = {
  preview: async (slug: string, eventCode: string, n: string) =>
    dataOf<EventPreview>(await http.get(`/api/v1/t/${slug}/e/${eventCode}/tables/${n}`)),
  mine: async () => dataOf<MyEvent>(await http.get('/api/v1/me/event')),
};

export function useMyEvent(enabled = true) {
  return useQuery({ queryKey: ['my-event'], queryFn: eventGuestApi.mine, retry: false, enabled });
}

export interface GuestRefund {
  refundId: string;
  restaurant: string;
  orderNumber: string;
  items: string[];
  amount: number;
  status: 'DUE' | 'SETTLED' | 'CONFIRMED';
  whatToDo: string;
  bankName?: string;
  accountName?: string;
  accountLast4?: string;
  needsEmail: boolean;
  guestEmail?: string;
  canAddBankDetails: boolean;
  canConfirmReceived: boolean;
  canReportMissing: boolean;
  reportedMissing: boolean;
}

export interface Bank {
  name: string;
  code: string;
}

// What to tell a guest whose code was refused, in words that fit the reason. Each refusal has its own title; a guest
// at the wrong table is not failing, so they are given directions instead.
export type CodeRefusal = { kind: 'directions'; title: string; message: string } | { kind: 'refusal'; title: string; message: string };

export function codeRefusal(failure: { message: string; status: number | null; offline?: boolean }): CodeRefusal {
  const message = failure.message;
  const wrong = /^Your seat is at (.+?)\.?$/i.exec(message);
  if (wrong) {
    return { kind: 'directions', title: "You're at the wrong table", message: `Your seat is at ${wrong[1]}. Head over there and scan its card.` };
  }
  if (failure.offline) return { kind: 'refusal', title: 'We lost the connection', message };
  if (/^Doors open at/i.test(message)) return { kind: 'refusal', title: "Doors aren't open yet", message };
  if (/event is over/i.test(message)) return { kind: 'refusal', title: 'This event is over', message };
  if (/can't find that code/i.test(message)) return { kind: 'refusal', title: "We can't find that code", message };
  if (/in use at/i.test(message)) return { kind: 'refusal', title: 'Your code is in use on another phone', message };
  if (/table is full/i.test(message)) return { kind: 'refusal', title: 'This table is full', message };
  if (/sitting in your seat/i.test(message)) return { kind: 'refusal', title: 'Someone is in your seat', message };
  if (/don't have a seat yet/i.test(message)) return { kind: 'refusal', title: "You don't have a seat yet", message };
  return { kind: 'refusal', title: "We couldn't seat you", message };
}
