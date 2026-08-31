import { api } from '@/lib/api';

export const kitchenService = {
  getOrders: (status?: string): Promise<any> => {
    const url = status ? `/api/v1/kitchen/orders?status=${status}` : '/api/v1/kitchen/orders';
    return api.get<any>(url)
      .then((res: any) => {
        const payload = res?.data?.data || res?.data || res || [];
        return Array.isArray(payload) ? payload : [];
      });
  },
  
  updateOrderStatus: (id: string, status: 'PREPARING' | 'READY'): Promise<any> => 
    api.patch(`/api/v1/kitchen/orders/${id}/status?status=${status}`)
      .then(res => res as any),
};
