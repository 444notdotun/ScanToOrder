import { apiClient } from './api';
import { Order, ServiceCall } from '@/types';
import { mockDb } from './mockDb';

export const waiterService = {
  getReadyOrders: () => 
    apiClient.get<Order[]>('/orders/ready')
      .then(res => res as any)
      .catch(() => mockDb.getReadyOrders()),

  markDelivered: (id: string) => 
    apiClient.patch(`/orders/${id}/deliver`)
      .then(res => res as any)
      .catch(() => mockDb.markDelivered(id)),

  getServiceCalls: () => 
    apiClient.get<ServiceCall[]>('/service-calls')
      .then(res => res as any)
      .catch(() => mockDb.getServiceCalls()),

  updateServiceCall: (id: string, payload: { status: 'IN_PROGRESS' | 'RESOLVED'; waiterId?: string }) => 
    apiClient.patch(`/service-calls/${id}`, payload)
      .then(res => res as any)
      .catch(() => mockDb.updateServiceCall(id, payload)),

  releaseSeat: (id: string) => 
    apiClient.post(`/seats/${id}/release`)
      .then(res => res as any)
      .catch(() => mockDb.releaseSeat(id)),

  closeSession: (id: string) => 
    apiClient.post(`/sessions/${id}/close`)
      .then(res => res as any)
      .catch(() => mockDb.closeSession(id)),
};
