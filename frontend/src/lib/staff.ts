import { useQuery } from '@tanstack/react-query';
import { dataOf, http } from '@/lib/http';

// One ticket on the pass, or on a waiter's list.
export interface ServiceOrder {
  orderId: string;
  orderNumber: string;
  tableNumber?: number;
  seatPosition?: number;
  guestName?: string;
  items: string[];
  note?: string;
  instructions?: string[];
  step: string;
  minutesWaiting: number;
  flag?: string;
  unavailableReason?: string;
}

export interface WaiterCall {
  callId: string;
  tableNumber?: number;
  where: string;
  what?: string;
  state: 'OPEN' | 'TAKEN' | 'DONE';
  minutesWaiting: number;
  takenBy?: string;
}

export interface FloorSeat {
  seatId: string;
  position: number;
  status: 'VACANT' | 'HELD' | 'OCCUPIED';
  secondsLeft?: number;
  guestFirstName?: string;
}

export interface FloorTable {
  tableId: string;
  tableNumber: string;
  displayNumber?: number;
  capacity: number;
  groups: number;
  seats: FloorSeat[];
}

export interface StockItem {
  itemId: string;
  itemName: string;
  categoryName?: string;
  available: boolean;
}

export type UnavailableReason = 'RAN_OUT' | 'SOMETHING_WRONG' | 'EQUIPMENT_DOWN';

export const UNAVAILABLE_REASONS: Array<{ reason: UnavailableReason; label: string; said: string }> = [
  { reason: 'RAN_OUT', label: 'We have run out', said: 'we have run out' },
  { reason: 'SOMETHING_WRONG', label: 'Something is wrong with it', said: 'something is wrong with it' },
  { reason: 'EQUIPMENT_DOWN', label: 'The equipment is down', said: 'the equipment is down' },
];

export function reasonSaid(reason?: string): string {
  return UNAVAILABLE_REASONS.find((r) => r.reason === reason)?.said ?? 'the kitchen cannot make it';
}

interface Done {
  message?: string;
}

export const staffApi = {
  kitchen: async () => dataOf<ServiceOrder[]>(await http.get('/api/v1/kitchen/orders')),
  ready: async () => dataOf<ServiceOrder[]>(await http.get('/api/v1/waiter/ready')),
  visits: async () => dataOf<ServiceOrder[]>(await http.get('/api/v1/waiter/visits')),
  calls: async () => dataOf<WaiterCall[]>(await http.get('/api/v1/waiter/calls')),
  tables: async () => dataOf<FloorTable[]>(await http.get('/api/v1/waiter/tables')),
  today: async () => dataOf<{ ordersToday: number }>(await http.get('/api/v1/staff/today')),
  stock: async () => dataOf<StockItem[]>(await http.get('/api/v1/menu/items/stock')),

  start: async (id: string) => dataOf<Done>(await http.post(`/api/v1/orders/${id}/start`)),
  markReady: async (id: string) => dataOf<Done>(await http.post(`/api/v1/orders/${id}/ready`)),
  deliver: async (id: string) => dataOf<Done>(await http.post(`/api/v1/orders/${id}/deliver`)),
  unavailable: async (id: string, reason: UnavailableReason) => dataOf<Done>(await http.post(`/api/v1/orders/${id}/unavailable`, { reason })),
  swap: async (id: string, items: Array<{ itemName: string; quantity: number }>, note: string) =>
    dataOf<Done>(
      await http.post(`/api/v1/orders/${id}/swap`, {
        items: items.map((item) => ({ ...item, specialInstructions: '' })),
        note: note.trim() || undefined,
      }),
    ),
  refundRequest: async (id: string) => dataOf<Done>(await http.post(`/api/v1/orders/${id}/refund-request`)),

  take: async (id: string) => dataOf<Done>(await http.post(`/api/v1/calls/${id}/take`)),
  done: async (id: string) => dataOf<Done>(await http.post(`/api/v1/calls/${id}/done`)),

  release: async (seatId: string) => dataOf<Done>(await http.post(`/api/v1/seats/${seatId}/release`)),
  sessions: async (tableId: string) => dataOf<{ paidUndeliveredOrders: number }>(await http.get(`/api/v1/tables/${tableId}/sessions`)),
  closeTable: async (tableId: string, confirm: boolean) => dataOf<Done>(await http.post(`/api/v1/tables/${tableId}/close`, null, { params: { confirm } })),

  soldOut: async (itemId: string) => dataOf<Done>(await http.post(`/api/v1/menu/items/${itemId}/sold-out`)),
  backInStock: async (itemId: string) => dataOf<Done>(await http.post(`/api/v1/menu/items/${itemId}/back-in-stock`)),
};

// Every list refetches when the live stream says something changed; nothing here asks the server on a timer.
export const useKitchen = () => useQuery({ queryKey: ['kitchen'], queryFn: staffApi.kitchen });
export const useReady = () => useQuery({ queryKey: ['ready'], queryFn: staffApi.ready });
export const useVisits = () => useQuery({ queryKey: ['visits'], queryFn: staffApi.visits });
export const useCalls = () => useQuery({ queryKey: ['calls'], queryFn: staffApi.calls });
export const useFloorTables = () => useQuery({ queryKey: ['tables'], queryFn: staffApi.tables });
export const useStock = () => useQuery({ queryKey: ['stock'], queryFn: staffApi.stock });

// "waiting 6 min" or "just now".
export function minutesText(minutes: number): string {
  return minutes <= 0 ? 'just now' : `${minutes} min`;
}
