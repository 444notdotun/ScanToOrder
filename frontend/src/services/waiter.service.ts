import { api } from '@/lib/api';
import { Order, ServiceCall } from '@/types';

export const waiterService = {
  getReadyOrders: (): Promise<any> => 
    api.get<Order[]>('/api/v1/kitchen/orders?status=READY')
      .then((res: any) => {
        const p = res?.data?.data || res?.data || res || [];
        return Array.isArray(p) ? p : [];
      }),

  markDelivered: (id: string): Promise<any> => 
    // Uses the discovered endpoint from OrderController: PATCH /api/v1/orders/{id}?status=DELIVERED
    api.patch(`/api/v1/orders/${id}?status=DELIVERED`)
      .then(res => res as any),

  getServiceCalls: (): Promise<any> => 
    api.get<any[]>('/api/v1/service-calls')
      .then((res: any) => {
        const raw = res?.data?.data || res?.data || res || [];
        if (!Array.isArray(raw)) return [];
        return raw.map((c: any) => {
          let reqType = 'ASSISTANCE';
          if (c.serviceDescription && c.serviceDescription.includes('ASSISTANCE')) reqType = 'WAITER';
          else if (c.serviceDescription && c.serviceDescription.includes('HELP')) reqType = 'HELP';
          
          let st = 'PENDING';
          if (c.serviceStatus === 'IN_PROGRESS') st = 'IN_PROGRESS';
          if (c.serviceStatus === 'RESOLVED') st = 'RESOLVED';

          return {
            id: c.serviceCallId,
            seatId: c.sessionId?.seats?.[0]?.seatId || '', // First seat if available
            seatNumber: c.sessionId?.seats?.[0]?.seatNumber,
            tableId: c.sessionId?.tableId?.tableId || '',
            tableNumber: c.sessionId?.tableId?.tableNumber || '?',
            requestType: reqType,
            status: st,
            note: c.serviceDescription,
            waiterId: c.assignedWorker?.workerId,
            waiterName: c.assignedWorker?.fullName,
            createdAt: c.createdAt || new Date().toISOString()
          };
        });
      }),

  updateServiceCall: (id: string, payload: { status: 'IN_PROGRESS' | 'RESOLVED'; waiterId?: string }): Promise<any> => 
    api.patch(`/api/v1/service-calls/${id}`, payload)
      .then(res => res as any),

  updateSeat: (payload: { seatId: string; newState: string }): Promise<any> =>
    api.patch('/api/v1/seats/update', payload)
      .then(res => res as any),

  releaseSeat: (id: string): Promise<any> => 
    api.post(`/api/v1/seats/${id}/release`)
      .then(res => res as any),

  closeSessionBySeat: (seatId: string): Promise<any> =>
    api.post(`/api/v1/seats/${seatId}/close-session`)
      .then(res => res as any),

  closeSession: (id: string): Promise<any> => 
    api.post(`/api/v1/sessions/${id}/close`)
      .then(res => res as any),
};
