export type ServiceCallType = 'WAITER' | 'BILL' | 'HELP' | 'CLEANING' | 'OTHER';

export type ServiceCallStatus = 'PENDING' | 'IN_PROGRESS' | 'RESOLVED';

export type SeatStatus = 'VACANT' | 'HELD' | 'OCCUPIED';

export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'PREPARING' | 'READY' | 'DELIVERED' | 'CANCELLED';

export type PaymentMethod = 'CARD' | 'BANK_TRANSFER' | 'CASH';

export type WorkerRole = 'WAITER' | 'CHEF' | 'MANAGER';

export type TableStatus = 'AVAILABLE' | 'OCCUPIED';

export interface RestaurantTable {
  /** Normalized id — set from backend's tableId or id */
  id: string;
  /** Backend UUID key (raw from API, kept for reference) */
  tableId?: string;
  tableNumber: string | number;
  capacity: number;
  status?: TableStatus;
  isOccupied?: boolean;
  isActive?: boolean;
  qrCodeUrl?: string;
  seats?: Seat[];
  currentSessionId?: string;
}

export interface Seat {
  id: string;
  tableId: string;
  seatNumber: number;
  status: SeatStatus;
  claimedBySessionId?: string | null;
}

export interface DiningSession {
  id: string;
  tableId: string;
  status: 'ACTIVE' | 'CLOSED';
  startTime: string;
  endTime?: string;
}

export interface Category {
  id: string;
  name: string;
  description?: string;
}

export interface MenuItem {
  id: string;
  name: string;
  description: string;
  price: number;
  imageUrl?: string;
  categoryId: string;
  categoryName?: string;
  available: boolean; // 86 toggle
}

export interface OrderItem {
  id: string;
  menuItemId: string;
  name: string;
  quantity: number;
  price: number;
  notes?: string;
  imageUrl?: string;
}

export interface Order {
  id: string;
  seatId: string;
  seatNumber?: number;
  tableId: string;
  tableNumber?: string | number;
  sessionId: string;
  status: OrderStatus;
  totalAmount: number;
  items: OrderItem[];
  createdAt: string;
  updatedAt?: string;
}

export interface ServiceCall {
  id: string;
  seatId: string;
  seatNumber?: number;
  tableId: string;
  tableNumber?: string | number;
  requestType: ServiceCallType;
  status: ServiceCallStatus;
  note?: string;
  waiterId?: string;
  waiterName?: string;
  createdAt: string;
}

export interface Worker {
  id: string;
  workerId?: string;
  name: string;
  role: WorkerRole;
  username: string;
}

export interface DashboardStats {
  todayRevenue: number;
  activeSessions: number;
  pendingOrdersCount: number;
  preparingOrdersCount: number;
  readyOrdersCount: number;
  averageRating: number;
}

export interface Review {
  id: string;
  sessionId: string;
  rating: number;
  reviewText: string;
  createdAt: string;
}

