import { create } from 'zustand';

// What a guest has picked so far, kept for the whole visit so the Basket count follows them from page to page.
// It lives in memory only: a refresh starts the basket again, as a fresh scan always has.
interface CartState {
  lines: Record<string, number>;
  change: (name: string, by: number) => void;
  clear: () => void;
}

export const useCart = create<CartState>()((set) => ({
  lines: {},
  change: (name, by) =>
    set((state) => {
      const next = Math.max(0, Math.min(20, (state.lines[name] ?? 0) + by));
      const lines = { ...state.lines };
      if (next === 0) delete lines[name];
      else lines[name] = next;
      return { lines };
    }),
  clear: () => set({ lines: {} }),
}));

export function cartCount(lines: Record<string, number>): number {
  return Object.values(lines).reduce((sum, quantity) => sum + quantity, 0);
}
