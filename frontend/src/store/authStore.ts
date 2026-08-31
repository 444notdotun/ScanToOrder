import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface WorkerProfile {
  username: string;
  role: string;
}

interface AuthState {
  workerProfile: WorkerProfile | null;
  isAuthenticated: boolean;
  login: (profile: WorkerProfile) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      workerProfile: null,
      isAuthenticated: false,
      login: (profile) => {
        if (typeof window !== 'undefined') {
          localStorage.setItem('user', JSON.stringify(profile));
        }
        set({ workerProfile: profile, isAuthenticated: true });
      },
      logout: () => {
        if (typeof window !== 'undefined') {
          localStorage.removeItem('user');
          // Important: to clear HttpOnly cookie, usually an API call is needed, or the backend clears it on 401. 
          // For now just clear local state.
        }
        set({ workerProfile: null, isAuthenticated: false });
      },
    }),
    {
      name: 'scan-to-order-auth-store',
      onRehydrateStorage: () => (state) => {
        if (typeof window !== 'undefined') {
          const storedUser = localStorage.getItem('user');
          if (storedUser) {
            try {
              const profile = JSON.parse(storedUser);
              if (profile) {
                state?.login(profile);
              }
            } catch (e) {}
          }
        }
      }
    }
  )
);
