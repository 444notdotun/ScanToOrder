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
}

export interface MenuSection {
  categoryName: string;
  itemResponse: MenuItem[];
}

export const guestApi = {
  seatMap: async (slug: string, n: string) => dataOf<SeatMap>(await http.get(`/api/v1/t/${slug}/tables/${n}/seatMap`)),
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
      })),
    }));
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
