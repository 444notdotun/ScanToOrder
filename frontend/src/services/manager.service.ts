import { api } from '@/lib/api';
import { MenuItem, Order, RestaurantTable, Worker } from '@/types';

export const managerService = {
  getAllOrders: (params?: Record<string, string>): Promise<any> => 
    api.get<Order[]>('/api/v1/orders', { params })
      .then(res => res as any),

  getMenuAdmin: (): Promise<MenuItem[]> => 
    api.get<any>('/api/v1/menu')
      .then(res => res as any)
      .then((menuData: any) => {
        const items: MenuItem[] = [];
        if (menuData && Array.isArray(menuData.categoryAndItemResponse)) {
          menuData.categoryAndItemResponse.forEach((cat: any) => {
            if (Array.isArray(cat.itemResponse)) {
              cat.itemResponse.forEach((item: any) => {
                items.push({
                  id: item.itemId,
                  name: item.itemName,
                  description: item.itemDescription,
                  price: item.itemPrice,
                  imageUrl: item.imageUrl || undefined,
                  categoryId: cat.categoryName,
                  categoryName: cat.categoryName,
                  available: item.isAvailable ?? item.available
                });
              });
            }
          });
        }
        return items;
      }),

  createMenuItem: (data: Partial<MenuItem>): Promise<any> => 
    api.post('/api/v1/items', {
      itemName: data.name,
      itemDescription: data.description,
      CategoryName: data.categoryName || 'Mains',
      itemPrice: data.price
    }).then(res => res as any),

  createCategory: (data: { categoryName: string }): Promise<any> =>
    api.post('/api/v1/categories', data)
      .then(res => res as any),

  updateMenuItem: (id: string, data: Partial<MenuItem>): Promise<any> => 
    api.put(`/api/v1/items/${id}`, {
      itemName: data.name,
      itemDescription: data.description,
      CategoryName: data.categoryName || 'Mains',
      itemPrice: data.price
    }).then(res => res as any),

  deleteMenuItem: (id: string): Promise<any> => 
    api.delete(`/api/v1/items/${id}`)
      .then(res => res as any),

  toggleMenuItemAvailability: (id: string): Promise<any> => 
    api.patch(`/api/v1/items/${id}/toggle`)
      .then(res => res as any),

  getTables: (): Promise<RestaurantTable[]> =>
    api.get<any>('/api/v1/tables')
      .then((res: any) => {
        // Defensively unpack: interceptor already strips the ApiResponse wrapper,
        // but guard against edge cases where wrapper is still present.
        const payload = res?.data ?? res ?? [];
        const raw: any[] = Array.isArray(payload)
          ? payload
          : (payload?.data ?? payload?.tables ?? payload?.content ?? []);

        return raw.map((t: any, index: number) => ({
          id:         t.tableId  || t.id || `table-${index}`,
          tableId:    t.tableId  || t.id,
          tableNumber: t.tableNumber ?? t.number ?? t.name ?? index + 1,
          capacity:   t.capacity ?? t.Capacity ?? t.seatCapacity ?? 0,
          status:     t.status   ?? (t.isOccupied ? 'OCCUPIED' : 'AVAILABLE'),
          isOccupied: t.isOccupied ?? (t.status === 'OCCUPIED'),
          isActive:   t.isActive ?? true,
          qrCodeUrl:  t.qrCodeUrl || t.qrCode || undefined,
          seats:      t.seats,
          currentSessionId: t.currentSessionId,
        })) as RestaurantTable[];
      }),

  createTable: (data: { tableNumber: number; capacity: number }): Promise<any> => 
    api.post('/api/v1/tables', data.capacity, {
      headers: {
        'Content-Type': 'application/json'
      }
    }).then(res => res as any),

  updateTable: (id: string, data: any): Promise<any> => 
    Promise.resolve(),

  deleteTable: (id: string): Promise<any> => 
    api.delete(`/api/v1/tables/${id}`)
      .then(res => res as any),

  getWorkers: (): Promise<any> => 
    api.get<Worker[]>('/api/v1/workers')
      .then(res => res as any),

  createWorker: (data: { name: string; role: string; username: string }): Promise<any> => 
    api.post<Worker>('/api/v1/workers', data)
      .then(res => res as any),

  updateWorker: (id: string, data: any): Promise<any> => 
    Promise.resolve(),

  deleteWorker: (id: string): Promise<any> => 
    api.delete(`/api/v1/workers/${id}`)
      .then(res => res as any),
};
