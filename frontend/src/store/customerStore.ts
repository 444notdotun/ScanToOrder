import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { MenuItem } from '@/types';

export interface CartItem {
  menuItem: MenuItem;
  quantity: number;
  notes?: string;
}

interface CustomerState {
  seatId: string | null;
  tableId: string | null;
  sessionId: string | null;
  cart: CartItem[];
  setSeat: (seatId: string, tableId: string, sessionId: string) => void;
  clearSeat: () => void;
  addToCart: (menuItem: MenuItem, quantity: number, notes?: string) => void;
  removeFromCart: (menuItemId: string) => void;
  updateCartQuantity: (menuItemId: string, quantity: number) => void;
  updateCartNotes: (menuItemId: string, notes: string) => void;
  clearCart: () => void;
}

export const useCustomerStore = create<CustomerState>()(
  persist(
    (set) => ({
      seatId: null,
      tableId: null,
      sessionId: null,
      cart: [],
      setSeat: (seatId, tableId, sessionId) =>
        set({ seatId, tableId, sessionId }),
      clearSeat: () =>
        set({ seatId: null, tableId: null, sessionId: null, cart: [] }),
      addToCart: (menuItem, quantity, notes) =>
        set((state) => {
          const existing = state.cart.find((item) => item.menuItem.id === menuItem.id);
          if (existing) {
            return {
              cart: state.cart.map((item) =>
                item.menuItem.id === menuItem.id
                  ? { ...item, quantity: item.quantity + quantity, notes: notes || item.notes }
                  : item
              ),
            };
          }
          return { cart: [...state.cart, { menuItem, quantity, notes }] };
        }),
      removeFromCart: (menuItemId) =>
        set((state) => ({
          cart: state.cart.filter((item) => item.menuItem.id !== menuItemId),
        })),
      updateCartQuantity: (menuItemId, quantity) =>
        set((state) => ({
          cart: state.cart.map((item) =>
            item.menuItem.id === menuItemId ? { ...item, quantity } : item
          ),
        })),
      updateCartNotes: (menuItemId, notes) =>
        set((state) => ({
          cart: state.cart.map((item) =>
            item.menuItem.id === menuItemId ? { ...item, notes } : item
          ),
        })),
      clearCart: () => set({ cart: [] }),
    }),
    {
      name: 'scan-to-order-customer-store',
    }
  )
);
