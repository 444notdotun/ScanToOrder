import { create } from 'zustand';

export type WaiterTab = 'carry' | 'visits' | 'calls' | 'tables';

// Which part of the waiter's floor is showing. The bottom bar and the header links both set it.
export const useWaiterTab = create<{ tab: WaiterTab; setTab: (tab: WaiterTab) => void }>()((set) => ({
  tab: 'carry',
  setTab: (tab) => set({ tab }),
}));
