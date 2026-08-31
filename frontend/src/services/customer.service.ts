import { api, publicApi } from '@/lib/api';
import { MenuItem, Order, ServiceCallType, Seat } from '@/types';
import { useCustomerStore } from '@/store/customerStore';

export const customerService = {
  getSeatStatuses: (): Promise<{ seatId: string; seatNumber: string; status: string }[]> =>
    publicApi.get<any>('/api/v1/seats/status')
      .then((res: any) => {
        const payload = res?.data ?? res ?? [];
        return Array.isArray(payload) ? payload : [];
      }),

  getTableSeatMap: (tableNumber: string): Promise<any> =>
    publicApi.get<any>(`/api/v1/tables/${tableNumber}/seatMap`)
      .then((res: any) => res?.data?.data || res?.data || res),

  getSeatMap: (tableNumber: string): Promise<any> =>
    customerService.getTableSeatMap(tableNumber),

  syncTableStatus: (tableNumber: string): Promise<any> => 
    publicApi.get<any>(`/api/v1/tables/${tableNumber}/status`)
      .then(res => res as any),

  claimSeat: (payload: { tableId: string; seatId: string; customerEmail?: string; customerName: string; customerPhoneNumber: string }): Promise<any> => {
    return api.post<any>('/api/v1/seats/claim', {
      seatId: payload.seatId,
      tableId: payload.tableId,
      customerEmail: payload.customerEmail || 'customer@example.com',
      customerName: payload.customerName,
      customerPhoneNumber: payload.customerPhoneNumber
    }).then(res => res as any);
  },

  getMenu: (): Promise<MenuItem[]> => 
    api.get<any>('/api/v1/menu')
      .then(res => res as any)
      .then((menuData: any) => {
        const items: MenuItem[] = [];
        if (menuData && Array.isArray(menuData.categoryAndItemResponse)) {
          menuData.categoryAndItemResponse.forEach((cat: any) => {
            if (Array.isArray(cat.itemResponse)) {
              cat.itemResponse.forEach((item: any) => {
                const lower = cat.categoryName.toLowerCase();
                let categoryId = 'c1';
                if (lower.includes('main') || lower.includes('meal') || lower.includes('food') || lower.includes('african')) {
                  categoryId = 'c1';
                } else if (lower.includes('appetizer') || lower.includes('start') || lower.includes('side')) {
                  categoryId = 'c2';
                } else if (lower.includes('drink') || lower.includes('beverage') || lower.includes('wine') || lower.includes('beer') || lower.includes('soft')) {
                  categoryId = 'c3';
                } else if (lower.includes('dessert') || lower.includes('sweet') || lower.includes('cake')) {
                  categoryId = 'c4';
                }

                items.push({
                  id: item.itemId,
                  name: item.itemName,
                  description: item.itemDescription,
                  price: item.itemPrice,
                  imageUrl: item.imageUrl || undefined,
                  categoryId: categoryId,
                  categoryName: cat.categoryName,
                  available: item.available
                });
              });
            }
          });
        }
        return items;
      }),

  createOrder: (payload: { seatId: string; items: { menuItemId: string; quantity: number; notes?: string }[] }): Promise<any> => {
    const storeState = useCustomerStore.getState();
    const tableId = storeState.tableId || '';
    const sessionId = storeState.sessionId || '';
    const cart = storeState.cart;

    const orderItems = payload.items.map(item => {
      const cartItem = cart.find(c => c.menuItem.id === item.menuItemId);
      const itemName = cartItem ? cartItem.menuItem.name : 'Unknown Item';
      return {
        itemName: itemName,
        quantity: item.quantity,
        specialInstructions: item.notes || ''
      };
    });

    return api.post<any>('/api/v1/orders', {
      seatId: payload.seatId,
      tableId: tableId,
      sessionId: sessionId,
      orderItems: orderItems
    }).then(res => res as any);
  },

  payOrder: (id: string, paymentMethod: string): Promise<any> => {
    if (paymentMethod !== 'CARD') {
      return api.patch<any>(`/api/v1/orders/${id}?status=PAID`)
        .then(res => res as any);
    }

    return api.post<any>('/api/v1/payments/initialize', {
      orderId: id,
      customerEmail: 'customer@example.com'
    }).then((res: any) => {
      if (res && res.authorizationUrl && typeof window !== 'undefined') {
        window.location.href = res.authorizationUrl;
      }
      return { success: true, paymentUrl: res?.authorizationUrl };
    });
  },

  getOrderStatus: (id: string): Promise<any> => 
    api.get<Order>(`/api/v1/orders/${id}`)
      .then(res => res as any),

  initializePayment: (payload: { orderId: string; customerEmail: string }): Promise<any> =>
    api.post<any>('/api/v1/payments/initialize', payload)
      .then(res => res as any),

  verifyPayment: (reference: string): Promise<any> =>
    api.get<any>(`/api/v1/payments/verify/${reference}`)
      .then(res => res as any),

  getReceipt: (reference: string): Promise<any> =>
    api.get<any>(`/api/v1/receipts/${reference}`)
      .then(res => res as any),

  downloadReceiptCsv: (reference: string): Promise<any> =>
    api.get(`/api/v1/receipts/${reference}/download`, { responseType: 'blob' })
      .then(res => res.data)
      .then((blob: Blob) => {
        const url = window.URL.createObjectURL(new Blob([blob]));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `receipt_${reference}.csv`);
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
      }),

  createServiceCall: (payload: { requestType: ServiceCallType; note?: string }): Promise<any> => 
    api.post('/api/v1/service-calls', payload)
      .then(res => res as any),

  getTableView: (): Promise<any> => 
    api.get('/api/v1/sessions/table-view')
      .then(res => res as any),

  submitReview: (payload: { sessionId: string; rating: number; reviewText: string }): Promise<any> => 
    api.post('/api/v1/sessions/review', payload)
      .then(res => res as any),
};
