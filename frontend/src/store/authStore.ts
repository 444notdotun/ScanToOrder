import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { useSyncExternalStore } from 'react';
import { http } from '@/lib/http';

export type StaffRole = 'MANAGER' | 'CHEF' | 'WAITER' | 'PLATFORM_ADMIN';

// Who is signed in. The sign-in itself is an HttpOnly cookie the page cannot read: this only remembers the name
// and the role, so the header can say who it is and each screen can send the wrong role home.
export interface WorkerProfile {
  workerId: string;
  username: string;
  role: StaffRole;
  fullName?: string;
  businessName?: string;
  mustChangePassword?: boolean;
  tenantType?: 'RESTAURANT' | 'EVENT_PLANNER';
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
      login: (profile) => set({ workerProfile: profile, isAuthenticated: true }),
      logout: () => {
        set({ workerProfile: null, isAuthenticated: false });
        // The cookie can only be ended by the server.
        void http.post('/api/v1/auth/logout').catch(() => undefined);
      },
    }),
    { name: 'plately-staff' },
  ),
);

// False until the remembered sign-in has been read from the browser.
export function useAuthHydrated(): boolean {
  return useSyncExternalStore(
    (notify) => useAuthStore.persist.onFinishHydration(notify),
    () => useAuthStore.persist.hasHydrated(),
    () => false,
  );
}

// Where each person starts their day. A planner's manager opens the events, not a restaurant floor.
export function homeFor(profile: Pick<WorkerProfile, 'role' | 'tenantType'>): string {
  if (profile.role === 'CHEF') return '/kitchen';
  if (profile.role === 'WAITER') return '/waiter';
  // The platform owner's own screens arrive in a later pull request.
  if (profile.role === 'PLATFORM_ADMIN') return '/';
  return profile.tenantType === 'EVENT_PLANNER' ? '/planner' : '/manager';
}
