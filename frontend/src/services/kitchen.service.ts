import { apiClient } from './api';
import { Order } from '@/types';
import { mockDb } from './mockDb';

export const kitchenService = {
  getOrders: () => 
    apiClient.get<Order[]>('/kitchen/orders')
      .then(res => res as any)
      .catch(() => mockDb.getKitchenOrders()),
  
  updateOrderStatus: (id: string, status: 'PREPARING' | 'READY') => 
    apiClient.patch(`/kitchen/orders/${id}/status`, { status })
      .then(res => res as any)
      .catch(() => mockDb.updateOrderStatus(id, status)),
};
