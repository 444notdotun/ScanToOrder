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
  capacity: number;
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
