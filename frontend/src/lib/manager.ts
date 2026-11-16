import { useQuery } from '@tanstack/react-query';
import { dataOf, http } from '@/lib/http';
import type { FloorTable, StockItem, WaiterCall } from '@/lib/staff';

export interface MyTenant {
  name: string;
  slug: string;
  email?: string;
  type: string;
  status: string;
  openForOrders: boolean;
  holdsEnabled: boolean;
  bankName?: string;
  accountLast4?: string;
}

export interface Category {
  categoryId: string;
  categoryName: string;
  active: boolean;
}

export interface TableRow {
  tableId: string;
  tableNumber: string;
  displayNumber?: number;
  capacity: number;
  status: string;
  isActive: boolean;
}

export interface Staff {
  workerId: string;
  username: string;
  fullName: string;
  role: 'MANAGER' | 'CHEF' | 'WAITER';
  temporaryPassword?: string;
}

export interface Floor {
  tables: FloorTable[];
  paid: number;
  cooking: number;
  ready: number;
  needVisit: number;
  openCalls: number;
  takenCalls: number;
  calls: WaiterCall[];
  openRefunds: number;
  overdueRefunds: number;
  soldOut: string[];
}

export type Period = 'today' | 'week' | 'month';

export interface Insights {
  period: Period;
  hasData: boolean;
  salesKobo: number;
  orders: number;
  platelyChargeKobo: number;
  receivedKobo: number;
  refundsKobo: number;
  ordersKitchenCouldNotMake: number;
  bestPlates: Array<{ name: string; quantity: number }>;
  busiestHours: Array<{ hour: number; orders: number }>;
  averageOrderKobo: number;
  paidToDeliveredMinutes?: number | null;
  guestsSeated: number;
  seatsHeld: number;
  seatsHeldTakenUp: number;
  waiterCalls: number;
  callToTakenMinutes?: number | null;
}

export type RefundState = 'open' | 'closed';

export interface ManagerRefund {
  refundId: string;
  guestName?: string;
  guestPhone?: string;
  tableNumber?: string;
  seatNumber?: string;
  tableDisplayNumber?: number;
  seatPosition?: number;
  orderNumber?: string;
  items?: string[];
  amount: number;
  paymentReference?: string;
  date?: string;
  reason?: string;
  status: string;
  closedBy?: string;
  guestLink?: string;
  guestEmail?: string;
  guestBankName?: string;
  guestAccountName?: string;
  guestAccountNumber?: string;
  overdue: boolean;
  reportedMissing: boolean;
  method?: string;
  transferReference?: string;
  settledAt?: string;
}

export interface Plate {
  itemName: string;
  itemDescription: string;
  categoryName: string;
  itemPrice: number;
}

interface Done {
  message?: string;
}

// The backend names the section field with a capital C.
function plateBody(plate: Plate) {
  return { itemName: plate.itemName, itemDescription: plate.itemDescription, CategoryName: plate.categoryName, itemPrice: plate.itemPrice };
}

export const managerApi = {
  tenant: async () => dataOf<MyTenant>(await http.get('/api/v1/tenant')),
  setOpen: async (open: boolean) => dataOf<MyTenant>(await http.patch('/api/v1/tenant/open-for-orders', JSON.stringify(open))),
  setHolds: async (enabled: boolean) => dataOf<MyTenant>(await http.patch('/api/v1/tenant/holds', JSON.stringify(enabled))),
  changePayout: async (body: { currentPassword: string; bankCode: string; accountNumber: string }) =>
    dataOf<MyTenant>(await http.put('/api/v1/tenant/payout-account', body)),
  banks: async () => dataOf<Array<{ name: string; code: string }>>(await http.get('/api/v1/signup/banks')),
  checkAccount: async (bankCode: string, accountNumber: string) =>
    dataOf<{ accountName: string }>(await http.post('/api/v1/signup/verify-account', { bankCode, accountNumber })),

  categories: async () => dataOf<Category[]>(await http.get('/api/v1/categories')),
  addCategory: async (categoryName: string) => dataOf<string>(await http.post('/api/v1/categories', { categoryName })),
  removeCategory: async (id: string) => dataOf<string>(await http.delete(`/api/v1/categories/${id}`)),
  plates: async () => dataOf<StockItem[]>(await http.get('/api/v1/menu/items/stock')),
  addPlate: async (plate: Plate) => dataOf<Done>(await http.post('/api/v1/items', plateBody(plate))),
  editPlate: async (id: string, plate: Plate) => dataOf<Done>(await http.put(`/api/v1/items/${id}`, plateBody(plate))),
  togglePlate: async (id: string) => dataOf<Done>(await http.patch(`/api/v1/items/${id}/toggle`)),
  removePlate: async (id: string) => dataOf<string>(await http.delete(`/api/v1/items/${id}`)),

  tables: async () => dataOf<TableRow[]>(await http.get('/api/v1/tables')),
  addTable: async (seatCapacity: number, displayNumber?: number) => dataOf<Done>(await http.post('/api/v1/tables', { seatCapacity, displayNumber })),
  removeTable: async (id: string) => dataOf<string>(await http.delete(`/api/v1/tables/${id}`)),
  qr: async (tableNumber: string) => (await http.get<Blob>(`/api/v1/tables/${tableNumber}/qrcode`, { responseType: 'blob' })).data,

  team: async () => dataOf<Staff[]>(await http.get('/api/v1/workers')),
  addStaff: async (body: { name: string; role: string; handle: string }) => dataOf<Staff>(await http.post('/api/v1/workers', body)),
  removeStaff: async (id: string) => dataOf<string>(await http.delete(`/api/v1/workers/${id}`)),

  floor: async () => dataOf<Floor>(await http.get('/api/v1/floor')),
  insights: async (period: Period) => dataOf<Insights>(await http.get('/api/v1/insights', { params: { period } })),
  refunds: async (state: RefundState) => dataOf<ManagerRefund[]>(await http.get('/api/v1/refunds', { params: { state } })),
  settle: async (id: string, method: 'CASH' | 'TRANSFER', transferReference: string) =>
    dataOf<ManagerRefund>(await http.post(`/api/v1/refunds/${id}/settle`, { method, transferReference: transferReference.trim() || undefined })),
};

// Every list refetches when the live stream says something changed; nothing here asks the server on a timer.
export const useTenant = () => useQuery({ queryKey: ['tenant'], queryFn: managerApi.tenant });
export const useCategories = () => useQuery({ queryKey: ['categories'], queryFn: managerApi.categories });
export const usePlates = () => useQuery({ queryKey: ['plates'], queryFn: managerApi.plates });
export const useTableRows = () => useQuery({ queryKey: ['table-rows'], queryFn: managerApi.tables });
export const useTeam = () => useQuery({ queryKey: ['team'], queryFn: managerApi.team });
export const useFloor = () => useQuery({ queryKey: ['floor'], queryFn: managerApi.floor });
export const useInsights = (period: Period) => useQuery({ queryKey: ['insights', period], queryFn: () => managerApi.insights(period) });
export const useRefunds = (state: RefundState) => useQuery({ queryKey: ['refunds', state], queryFn: () => managerApi.refunds(state) });
export const useBanks = () => useQuery({ queryKey: ['banks'], queryFn: managerApi.banks, staleTime: Infinity });
