import { apiClient } from './api';
import { MenuItem, Order, ServiceCallType, Seat } from '@/types';
import { mockDb } from './mockDb';

export const customerService = {
  getSeatMap: (tableId: string) => 
    apiClient.get<any>(`/tables/${tableId}/seat-map`)
      .then(res => res as any)
      .catch(() => mockDb.getSeatMap(tableId)),

  claimSeat: (seatId: string) => 
    apiClient.post<{ token: string; seatId: string; tableId: string; sessionId: string }>(`/seats/${seatId}/claim`)
      .then(res => res as any)
      .catch(() => mockDb.claimSeat(seatId)),

  getMenu: () => 
    apiClient.get<MenuItem[]>('/menu')
      .then(res => res as any)
      .catch(() => mockDb.getMenu()),

  createOrder: (payload: { seatId: string; items: { menuItemId: string; quantity: number; notes?: string }[] }) => 
    apiClient.post<{ orderId: string; totalAmount: number }>('/orders', payload)
      .then(res => res as any)
      .catch(() => mockDb.createOrder(payload)),

  payOrder: (id: string, paymentMethod: string) => 
    apiClient.post<{ paymentUrl?: string; success: boolean }>(`/orders/${id}/pay`, { paymentMethod })
      .then(res => res as any)
      .catch(() => mockDb.payOrder(id, paymentMethod)),

  getOrderStatus: (id: string) => 
    apiClient.get<Order>(`/orders/${id}`)
      .then(res => res as any)
      .catch(() => mockDb.getOrderStatus(id)),

  createServiceCall: (payload: { seatId: string; tableId: string; requestType: ServiceCallType; note?: string }) => 
    apiClient.post('/service-calls', payload)
      .then(res => res as any)
      .catch(() => mockDb.createServiceCall(payload)),

  getTableView: () => 
    apiClient.get('/sessions/table-view')
      .then(res => res as any)
      .catch(() => mockDb.getTables()),

  submitReview: (payload: { sessionId: string; rating: number; reviewText: string }) => 
    apiClient.post('/sessions/review', payload)
      .then(res => res as any)
      .catch(() => mockDb.submitReview(payload)),
};
